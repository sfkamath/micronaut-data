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

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.recovery.CommitOutcome;
import io.micronaut.transaction.recovery.CommitOutcomeResolver;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;

/**
 * Oracle Transaction Guard implementation of {@link CommitOutcomeResolver}.
 *
 * <p>This implementation:
 * <ul>
 *   <li>Captures the current Logical Transaction ID (LTXID) using SYS_CONTEXT('USERENV','LTXID') on the active connection.</li>
 *   <li>Resolves the commit outcome by calling DBMS_APP_CONT.GET_LTXID_OUTCOME on a fresh connection.</li>
 * </ul>
 * </p>
 *
 * <p>Activation is controlled by {@link OracleTransactionGuardCondition} and is created per-DataSource via {@code @EachBean}.</p>
 *
 * @since 5.0
 */
@Internal
@EachBean(DataSource.class)
@Requires(condition = OracleTransactionGuardCondition.class)
final class OracleTransactionGuardResolver implements CommitOutcomeResolver {

    private static final Logger LOG = LoggerFactory.getLogger(OracleTransactionGuardResolver.class);

    private final DataSource dataSource;

    OracleTransactionGuardResolver(@NonNull @Parameter DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public @Nullable Object captureLtxid(@NonNull TransactionStatus<?> status) {
        // Query the LTXID from the current session/transaction
        Object connection = status.getConnection();
        if (!(connection instanceof Connection conn)) {
            return null;
        }
        // Use a simple query to avoid driver-specific APIs
        final String sql = "select sys_context('USERENV','LTXID') from dual";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                String ltxid = rs.getString(1);
                if (ltxid != null && !ltxid.isEmpty()) {
                    return ltxid;
                }
            }
        } catch (SQLException e) {
            // Non-fatal: if we cannot capture, downstream will treat outcome as UNKNOWN
            LOG.debug("Failed to capture LTXID from current transaction", e);
        }
        return null;
    }

    @Override
    public @NonNull CommitOutcome resolve(@NonNull Object ltxidToken) {
        String hexLtxid = toHexString(ltxidToken);
        if (hexLtxid == null || hexLtxid.isEmpty()) {
            return CommitOutcome.UNKNOWN;
        }
        // Query TG outcome on a fresh connection
        // Prefer a plain SELECT to avoid CallableStatement portability issues:
        // select sys.dbms_app_cont.get_ltxid_outcome(hextoraw(?)) from dual
        final String sql = "select sys.dbms_app_cont.get_ltxid_outcome(hextoraw(?)) from dual";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hexLtxid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int code = rs.getInt(1);
                    return mapOutcome(code);
                }
            }
        } catch (SQLException e) {
            LOG.debug("Failed to resolve commit outcome for LTXID {}", hexLtxid, e);
        }
        return CommitOutcome.UNKNOWN;
    }

    @Nullable
    private static String toHexString(@NonNull Object token) {
        if (token instanceof String s) {
            // Oracle USERENV LTXID returns hex string already
            return s;
        }
        if (token instanceof byte[] bytes) {
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString().toUpperCase();
        }
        return null;
    }

    /**
     * Map DBMS_APP_CONT outcome code to CommitOutcome.
     * <p>Conservative mapping:
     * <ul>
     *   <li>1 => COMMITTED</li>
     *   <li>0 or 2 => NOT_COMMITTED</li>
     *   <li>otherwise => UNKNOWN</li>
     * </ul>
     * </p>
     */
    private static CommitOutcome mapOutcome(int code) {
        if (code == 1) {
            return CommitOutcome.COMMITTED;
        }
        if (code == 0 || code == 2) {
            return CommitOutcome.NOT_COMMITTED;
        }
        return CommitOutcome.UNKNOWN;
    }
}
