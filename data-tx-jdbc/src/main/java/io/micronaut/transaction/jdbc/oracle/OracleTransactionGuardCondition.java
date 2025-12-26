/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.transaction.jdbc.oracle;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.Named;
import io.micronaut.inject.BeanDefinition;

/**
 * Condition enabling Transaction Guard integration for Oracle datasources when configured.
 *
 * <p>Enabled when:
 * <ul>
 *   <li>datasources.<name>.dialect == ORACLE</li>
 *   <li>datasources.<name>.enable-oracle-transaction-guard == true</li>
 * </ul>
 * </p>
 *
 * @since 5.0
 */
@Internal
final class OracleTransactionGuardCondition implements Condition {

    private static final String DATASOURCES = "datasources";
    private static final String DIALECT = "dialect";
    private static final String ORACLE_DIALECT = "ORACLE";
    private static final String ENABLE_TG = "enable-oracle-transaction-guard";

    @Override
    public boolean matches(ConditionContext context) {
        BeanResolutionContext brc = context.getBeanResolutionContext();
        String dataSourceName;
        if (brc == null) {
            // Cannot resolve DS name; be permissive
            return true;
        } else {
            Qualifier<?> currentQualifier = brc.getCurrentQualifier();
            if (currentQualifier == null && context.getComponent() instanceof BeanDefinition<?> definition) {
                currentQualifier = definition.getDeclaredQualifier();
            }
            if (currentQualifier instanceof Named named) {
                dataSourceName = named.getName();
            } else {
                dataSourceName = "default";
            }
        }
        String prefix = DATASOURCES + "." + dataSourceName + ".";
        String dialect = context.getProperty(prefix + DIALECT, String.class).orElse(null);
        if (!ORACLE_DIALECT.equalsIgnoreCase(dialect)) {
            return false;
        }
        return context.getProperty(prefix + ENABLE_TG, Boolean.class, false);
    }
}
