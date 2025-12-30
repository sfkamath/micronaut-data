/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.hibernate.reactive.operations;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
import io.micronaut.data.hibernate.conf.RequiresReactiveHibernate;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.operations.HintsCapableRepository;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.async.AsyncRepositoryOperations;
import io.micronaut.data.operations.async.AsyncCriteriaRepositoryOperations;
import io.micronaut.data.operations.reactive.BlockingExecutorReactorRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactorReactiveRepositoryOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.operations.AsyncFromReactiveAsyncRepositoryOperation;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import io.micronaut.data.runtime.query.StoredQueryDecorator;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import jakarta.annotation.PreDestroy;
import org.hibernate.SessionFactory;
import org.hibernate.reactive.stage.Stage;
import reactor.core.publisher.Mono;
import io.micronaut.data.operations.reactive.BlockingReactorCriteriaRepositoryOperations;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.CriteriaDelete;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContext;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Hibernate reactive implementation of {@link JpaRepositoryOperations}.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@RequiresReactiveHibernate
@EachBean(SessionFactory.class)
@Internal
final class DefaultHibernateReactiveSynchronousRepositoryOperations implements BlockingExecutorReactorRepositoryOperations,
        JpaRepositoryOperations, AsyncCapableRepository, HintsCapableRepository, Closeable, PreparedQueryDecorator, StoredQueryDecorator,
        BlockingReactorCriteriaRepositoryOperations {

    private final ApplicationContext applicationContext;
    private final DefaultHibernateReactiveRepositoryOperations reactiveRepositoryOperations;
    private AsyncRepositoryOperations asyncRepositoryOperations;
    private ExecutorService executorService;

    public DefaultHibernateReactiveSynchronousRepositoryOperations(ApplicationContext applicationContext,
                                                                   SessionFactory sessionFactory,
                                                                   RuntimeEntityRegistry runtimeEntityRegistry,
                                                                   DataConversionService dataConversionService,
                                                                   @Parameter ReactorConnectionOperations<Stage.Session> connectionOperations,
                                                                   @Parameter ReactorReactiveTransactionOperations<Stage.Session> transactionOperations) {
        this.applicationContext = applicationContext;
        this.reactiveRepositoryOperations = new DefaultHibernateReactiveRepositoryOperations(sessionFactory,
            runtimeEntityRegistry, dataConversionService, connectionOperations, transactionOperations);
    }

    @Override
    public <T> T block(Function<ReactorReactiveRepositoryOperations, Mono<T>> supplier) {
        return supplier.apply(reactiveRepositoryOperations)
            .contextWrite(getContextView())
            .block();
    }

    @Override
    public <T> Optional<T> blockOptional(Function<ReactorReactiveRepositoryOperations, Mono<T>> supplier) {
        return supplier.apply(reactiveRepositoryOperations)
            .contextWrite(getContextView())
            .blockOptional();
    }

    @Override
    public EntityManager getCurrentEntityManager() {
        return notSupported();
    }

    @Override
    public EntityManagerFactory getEntityManagerFactory() {
        return notSupported();
    }

    @Override
    public <T> T load(Class<T> type, Object id) {
        return notSupported();
    }

    @Override
    public <T> T merge(T entity) {
        return notSupported();
    }

    @Override
    public <T> void persist(T entity) {
        notSupported();
    }

    @Override
    public <T> void refresh(T entity) {
        notSupported();
    }

    @Override
    public <T> void remove(T entity) {
        notSupported();
    }

    @Override
    public <T> void detach(T entity) {
        notSupported();
    }

    @Override
    public void flush() {
        notSupported();
    }

    @Override
    public AsyncRepositoryOperations async() {
        if (asyncRepositoryOperations == null) {
            if (executorService == null) {
                executorService = Executors.newCachedThreadPool();
            }
            // Return async operations exposing AsyncCriteriaRepositoryOperations without blocking, delegating to reactive operations
            asyncRepositoryOperations = new AsyncCriteriaFromReactiveAdapter(reactiveRepositoryOperations, executorService);
        }
        return asyncRepositoryOperations;
    }

    @Override
    public DefaultHibernateReactiveRepositoryOperations reactive() {
        return reactiveRepositoryOperations;
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        return reactiveRepositoryOperations.getCriteriaBuilder();
    }

    private ContextView getContextView() {
        return ReactorPropagation.addPropagatedContext(Context.empty(), PropagatedContext.getOrEmpty());
    }

    @Override
    public boolean exists(CriteriaQuery<?> query) {
        return Mono.from(reactiveRepositoryOperations.exists(query))
            .contextWrite(getContextView())
            .blockOptional()
            .orElse(false);
    }

    @Override
    public <R> R findOne(CriteriaQuery<R> query) {
        return reactiveRepositoryOperations.findOne(query)
            .contextWrite(getContextView())
            .block();
    }

    @Override
    public <T> List<T> findAll(CriteriaQuery<T> query) {
        return reactiveRepositoryOperations.findAll(query)
            .collectList()
            .contextWrite(getContextView())
            .blockOptional()
            .orElseGet(List::of);
    }

    @Override
    public <T> List<T> findAll(CriteriaQuery<T> query, int offset, int limit) {
        return reactiveRepositoryOperations.findAll(query, offset, limit)
            .collectList()
            .contextWrite(getContextView())
            .blockOptional()
            .orElseGet(List::of);
    }

    @Override
    public Optional<Number> updateAll(CriteriaUpdate<Number> query) {
        return reactiveRepositoryOperations.updateAll(query)
            .contextWrite(getContextView())
            .blockOptional();
    }

    @Override
    public Optional<Number> deleteAll(CriteriaDelete<Number> query) {
        return reactiveRepositoryOperations.deleteAll(query)
            .contextWrite(getContextView())
            .blockOptional();
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public Map<String, Object> getQueryHints(StoredQuery<?, ?> storedQuery) {
        return reactive().getQueryHints(storedQuery);
    }

    @PreDestroy
    @Override
    public void close() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private IllegalStateException blockingNotSupported() {
        return new IllegalStateException("Blocking isn't supported for Hibernate Reactive");
    }

    private <T> T notSupported() {
        throw new IllegalStateException("Method isn't supported for Hibernate Reactive");
    }

    @Override
    public <E, R> PreparedQuery<E, R> decorate(PreparedQuery<E, R> preparedQuery) {
        return reactiveRepositoryOperations.decorate(preparedQuery);
    }

    @Override
    public <E, R> StoredQuery<E, R> decorate(StoredQuery<E, R> storedQuery) {
        return reactiveRepositoryOperations.decorate(storedQuery);
    }

    @Override
    public ConversionService getConversionService() {
        return reactiveRepositoryOperations.getConversionService();
    }

    private static final class AsyncCriteriaFromReactiveAdapter extends AsyncFromReactiveAsyncRepositoryOperation implements AsyncCriteriaRepositoryOperations {

        private final DefaultHibernateReactiveRepositoryOperations reactiveOps;

        private AsyncCriteriaFromReactiveAdapter(DefaultHibernateReactiveRepositoryOperations reactiveOps, java.util.concurrent.Executor executor) {
            super(reactiveOps, executor);
            this.reactiveOps = reactiveOps;
        }

        @Override
        public CriteriaBuilder getCriteriaBuilder() {
            return reactiveOps.getCriteriaBuilder();
        }

        @Override
        public java.util.concurrent.CompletionStage<Boolean> exists(CriteriaQuery<?> query) {
            return ((reactor.core.publisher.Mono<Boolean>) reactiveOps.exists(query)).toFuture();
        }

        @Override
        public <R> java.util.concurrent.CompletionStage<R> findOne(CriteriaQuery<R> query) {
            return reactiveOps.findOne(query).toFuture();
        }

        @Override
        public <T> java.util.concurrent.CompletionStage<java.util.List<T>> findAll(CriteriaQuery<T> query) {
            return reactiveOps.findAll(query).collectList().toFuture();
        }

        @Override
        public <T> java.util.concurrent.CompletionStage<java.util.List<T>> findAll(CriteriaQuery<T> query, int offset, int limit) {
            return reactiveOps.findAll(query, offset, limit).collectList().toFuture();
        }

        @Override
        public java.util.concurrent.CompletionStage<Number> updateAll(CriteriaUpdate<Number> query) {
            return reactiveOps.updateAll(query).toFuture();
        }

        @Override
        public java.util.concurrent.CompletionStage<Number> deleteAll(CriteriaDelete<Number> query) {
            return reactiveOps.deleteAll(query).toFuture();
        }
    }
}
