/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.transaction.interceptor;

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionOperationsRegistry;
import io.micronaut.transaction.annotation.Transactional;
import io.micronaut.transaction.async.AsyncTransactionOperations;
import io.micronaut.transaction.reactive.ReactiveTransactionOperations;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import io.micronaut.transaction.support.TransactionUtil;
import io.micronaut.transaction.annotation.Recoverable;
import io.micronaut.transaction.recovery.CommitOutcome;
import io.micronaut.transaction.recovery.CommitOutcomeResolver;
import io.micronaut.context.BeanLocator;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link Transactional}. Forked from the reflection based code in Spring.
 *
 * @author graemerocher
 * @author Denis stepanov
 * @since 1.0
 */
@Singleton
@Internal
public final class TransactionalInterceptor implements MethodInterceptor<Object, Object> {

    private final Map<TenantExecutableMethod, TransactionInvocation> transactionInvocationMap = new ConcurrentHashMap<>(30);

    @NonNull
    private final TransactionOperationsRegistry transactionOperationsRegistry;
    @Nullable
    private final TransactionDataSourceTenantResolver tenantResolver;

    private final ConversionService conversionService;

    private final BeanLocator beanLocator;

    /**
     * Default constructor.
     *
     * @param transactionOperationsRegistry The {@link TransactionOperationsRegistry}
     * @param tenantResolver                The {@link TransactionDataSourceTenantResolver}
     * @param conversionService             The conversion service
     * @param beanLocator                   The bean locator
     */
    public TransactionalInterceptor(@NonNull TransactionOperationsRegistry transactionOperationsRegistry,
                                    @Nullable TransactionDataSourceTenantResolver tenantResolver,
                                    ConversionService conversionService,
                                    BeanLocator beanLocator) {
        this.transactionOperationsRegistry = transactionOperationsRegistry;
        this.tenantResolver = tenantResolver;
        this.conversionService = conversionService;
        this.beanLocator = beanLocator;
    }

    @Override
    public int getOrder() {
        return InterceptPhase.TRANSACTION.getPosition();
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        String tenantDataSourceName;
        if (tenantResolver != null) {
            tenantDataSourceName = tenantResolver.resolveTenantDataSourceName();
        } else {
            tenantDataSourceName = null;
        }
        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        try {
            ExecutableMethod<Object, Object> executableMethod = context.getExecutableMethod();
            final TransactionInvocation<?> transactionInvocation = transactionInvocationMap
                .computeIfAbsent(new TenantExecutableMethod(tenantDataSourceName, executableMethod), ignore -> {
                    final String dataSource = tenantDataSourceName == null ? executableMethod.stringValue(Transactional.class).orElse(null) : tenantDataSourceName;
                    final TransactionDefinition transactionDefinition = resolveTransactionDefinition(executableMethod);

                    switch (interceptedMethod.resultType()) {
                        case PUBLISHER -> {
                            ReactiveTransactionOperations<?> reactiveTransactionOperations = transactionOperationsRegistry.provideReactive(ReactiveTransactionOperations.class, dataSource);
                            return new TransactionInvocation<>(null, reactiveTransactionOperations, null, transactionDefinition);
                        }
                        case COMPLETION_STAGE -> {
                            AsyncTransactionOperations<?> asyncTransactionOperations = transactionOperationsRegistry.provideAsync(AsyncTransactionOperations.class, dataSource);
                            return new TransactionInvocation<>(null, null, asyncTransactionOperations, transactionDefinition);
                        }
                        default -> {
                            TransactionOperations<?> transactionManager = transactionOperationsRegistry.provideSynchronous(TransactionOperations.class, dataSource);
                            return new TransactionInvocation<>(transactionManager, null, null, transactionDefinition);
                        }
                    }
                });

            final TransactionDefinition definition = transactionInvocation.definition;
            switch (interceptedMethod.resultType()) {
                case PUBLISHER -> {
                    ReactiveTransactionOperations<?> reactiveTransactionOperations = Objects.requireNonNull(transactionInvocation.reactiveTransactionOperations);
                    if (reactiveTransactionOperations instanceof ReactorReactiveTransactionOperations<?> reactorTransactionOperations) {
                        if (context.getReturnType().isSingleResult()) {
                            return interceptedMethod.handleResult(
                                reactorTransactionOperations.withTransactionMono(definition, status -> Mono.from(interceptedMethod.interceptResultAsPublisher()))
                            );
                        }
                        return interceptedMethod.handleResult(
                            reactorTransactionOperations.withTransactionFlux(definition, status -> Flux.from(interceptedMethod.interceptResultAsPublisher()))
                        );
                    }
                    return interceptedMethod.handleResult(
                        reactiveTransactionOperations.withTransaction(definition, (status) -> interceptedMethod.interceptResultAsPublisher())
                    );
                }
                case COMPLETION_STAGE -> {
                    AsyncTransactionOperations<?> asyncTransactionOperations = Objects.requireNonNull(transactionInvocation.asyncTransactionOperations);
                    return interceptedMethod.handleResult(
                        asyncTransactionOperations.withTransaction(definition, status -> interceptedMethod.interceptResultAsCompletionStage())
                    );
                }
                case SYNCHRONOUS -> {
                    TransactionOperations<?> transactionManager = Objects.requireNonNull(transactionInvocation.transactionManager);

                    // Recoverable handling (synchronous only)
                    if (context.getAnnotationMetadata().hasAnnotation(Recoverable.class)) {
                        // Resolve data source name for resolver lookup (same logic as TM resolution)
                        final String dataSource = tenantDataSourceName == null ? context.getExecutableMethod().stringValue(Transactional.class).orElse(null) : tenantDataSourceName;

                        final CommitOutcomeResolver resolver = findOutcomeResolver(dataSource);
                        if (resolver != null) {
                            // Read annotation attributes
                            Class<?>[] on = context.classValues(Recoverable.class, "on");
                            if (on == null || on.length == 0) {
                                on = new Class[]{java.sql.SQLRecoverableException.class};
                            }
                            final int maxAttempts = context.intValue(Recoverable.class, "maxAttempts").orElse(1);
                            final long backoff = context.longValue(Recoverable.class, "backoff").orElse(100L);
                            final Recoverable.OutcomePolicy unknownPolicy =
                                context.enumValue(Recoverable.class, "unknownOutcomePolicy", Recoverable.OutcomePolicy.class)
                                    .orElse(Recoverable.OutcomePolicy.RETRY);

                            int attempts = 0;
                            while (true) {
                                final AtomicReference<Object> ltxidRef = new AtomicReference<>();
                                final AtomicReference<Object> resultRef = new AtomicReference<>();
                                try {
                                    return transactionManager.execute(definition, status -> {
                                        // Capture LTXID/token right before commit
                                        status.registerSynchronization(new io.micronaut.transaction.support.TransactionSynchronization() {
                                            @Override
                                            public void beforeCompletion() {
                                                Object token = resolver.captureLtxid(status);
                                                if (token != null) {
                                                    ltxidRef.set(token);
                                                }
                                            }
                                        });
                                        Object r = context.proceed();
                                        resultRef.set(r);
                                        return r;
                                    });
                                } catch (Throwable t) {
                                    if (!matchesRecoverable(t, on)) {
                                        throw t;
                                    }
                                    CommitOutcome outcome = CommitOutcome.UNKNOWN;
                                    Object token = ltxidRef.get();
                                    if (token != null) {
                                        try {
                                            outcome = resolver.resolve(token);
                                        } catch (Throwable ignore) {
                                            // leave UNKNOWN
                                        }
                                    }
                                    if (outcome == CommitOutcome.COMMITTED) {
                                        return resultRef.get();
                                    }
                                    boolean retry = outcome == CommitOutcome.NOT_COMMITTED ||
                                        (outcome == CommitOutcome.UNKNOWN && unknownPolicy == Recoverable.OutcomePolicy.RETRY);
                                    if (retry && attempts++ < maxAttempts) {
                                        if (backoff > 0) {
                                            try {
                                                Thread.sleep(backoff);
                                            } catch (InterruptedException ie) {
                                                Thread.currentThread().interrupt();
                                            }
                                        }
                                        // retry new TX
                                        continue;
                                    }
                                    throw t;
                                }
                            }
                        }
                    }

                    // Default synchronous transactional execution
                    return transactionManager.execute(definition, status -> context.proceed());
                }
                default -> {
                    return interceptedMethod.unsupported();
                }
            }
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }

    /**
     * @param executableMethod The method
     * @return The {@link TransactionDefinition}
     */
    private TransactionDefinition resolveTransactionDefinition(ExecutableMethod<Object, Object> executableMethod) {
        String name = executableMethod.stringValue(Transactional.class, "name")
            .orElseGet(() -> executableMethod.getDeclaringType().getSimpleName() + "." + executableMethod.getMethodName());
        TransactionDefinition definition = TransactionUtil.getTransactionDefinition(name, executableMethod);
        if (definition == TransactionDefinition.DEFAULT) {
            throw new IllegalStateException("No declared @Transactional annotation present");
        }
        return definition;
    }

    @Nullable
    private CommitOutcomeResolver findOutcomeResolver(@Nullable String dataSourceName) {
        try {
            if (dataSourceName == null) {
                return beanLocator.findBean(CommitOutcomeResolver.class).orElse(null);
            }
            return beanLocator.findBean(CommitOutcomeResolver.class, Qualifiers.byName(dataSourceName)).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean matchesRecoverable(Throwable t, Class<?>[] types) {
        Throwable cur = t;
        while (cur != null) {
            for (Class<?> c : types) {
                if (c.isInstance(cur)) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    /**
     * Cached invocation associating a method with a definition a transaction manager.
     *
     * @param transactionManager            The transaction manager
     * @param reactiveTransactionOperations The reactive transaction manager
     * @param asyncTransactionOperations    The async transaction manager
     * @param definition                    The definition
     * @param <C>                           connection type
     */
    private record TransactionInvocation<C>(@Nullable TransactionOperations<C> transactionManager,
                                            @Nullable ReactiveTransactionOperations<C> reactiveTransactionOperations,
                                            @Nullable AsyncTransactionOperations<C> asyncTransactionOperations,
                                            TransactionDefinition definition) {

    }

    private record TenantExecutableMethod(String dataSource, ExecutableMethod method) {
    }
}
