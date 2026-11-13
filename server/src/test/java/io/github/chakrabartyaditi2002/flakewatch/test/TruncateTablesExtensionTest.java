package io.github.chakrabartyaditi2002.flakewatch.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TruncateTablesExtensionTest {

    @Test
    @DisplayName("T-0.8: TruncateTablesExtension issues TRUNCATE TABLE with RESTART IDENTITY CASCADE")
    void truncatesAllDiscoveredTables() throws Exception {
        List<String> executedSql = new ArrayList<>();
        List<String> tableRows = List.of("users", "projects", "api_tokens");
        AtomicInteger rowIndex = new AtomicInteger(0);

        ResultSet rs = (ResultSet) Proxy.newProxyInstance(
            ResultSet.class.getClassLoader(),
            new Class<?>[] {ResultSet.class},
            (proxy, method, args) -> {
                if (method.getName().equals("next")) {
                    return rowIndex.getAndIncrement() < tableRows.size();
                }
                if (method.getName().equals("getString")) {
                    return tableRows.get(rowIndex.get() - 1);
                }
                if (method.getName().equals("close")) {
                    return null;
                }
                return defaultValue(method.getReturnType());
            });

        Statement stmt = (Statement) Proxy.newProxyInstance(
            Statement.class.getClassLoader(),
            new Class<?>[] {Statement.class},
            (proxy, method, args) -> {
                if (method.getName().equals("executeQuery")) {
                    return rs;
                }
                if (method.getName().equals("execute")) {
                    executedSql.add((String) args[0]);
                    return true;
                }
                if (method.getName().equals("close")) {
                    return null;
                }
                return defaultValue(method.getReturnType());
            });

        Connection conn = (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
                if (method.getName().equals("createStatement")) {
                    return stmt;
                }
                if (method.getName().equals("close")) {
                    return null;
                }
                return defaultValue(method.getReturnType());
            });

        DataSource ds = (DataSource) Proxy.newProxyInstance(
            DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getConnection")) {
                    return conn;
                }
                return defaultValue(method.getReturnType());
            });

        TruncateTablesExtension.truncateAllTables(ds);

        assertThat(executedSql)
            .containsExactly("TRUNCATE TABLE users, projects, api_tokens RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("T-0.8: TruncateTablesExtension does not issue TRUNCATE when no tables exist")
    void doesNotTruncateWhenNoTables() throws Exception {
        List<String> executedSql = new ArrayList<>();

        ResultSet rs = (ResultSet) Proxy.newProxyInstance(
            ResultSet.class.getClassLoader(),
            new Class<?>[] {ResultSet.class},
            (proxy, method, args) -> {
                if (method.getName().equals("next")) {
                    return false;
                }
                if (method.getName().equals("close")) {
                    return null;
                }
                return defaultValue(method.getReturnType());
            });

        Statement stmt = (Statement) Proxy.newProxyInstance(
            Statement.class.getClassLoader(),
            new Class<?>[] {Statement.class},
            (proxy, method, args) -> {
                if (method.getName().equals("executeQuery")) {
                    return rs;
                }
                if (method.getName().equals("execute")) {
                    executedSql.add((String) args[0]);
                    return true;
                }
                if (method.getName().equals("close")) {
                    return null;
                }
                return defaultValue(method.getReturnType());
            });

        Connection conn = (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
                if (method.getName().equals("createStatement")) {
                    return stmt;
                }
                if (method.getName().equals("close")) {
                    return null;
                }
                return defaultValue(method.getReturnType());
            });

        DataSource ds = (DataSource) Proxy.newProxyInstance(
            DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getConnection")) {
                    return conn;
                }
                return defaultValue(method.getReturnType());
            });

        TruncateTablesExtension.truncateAllTables(ds);

        assertThat(executedSql).isEmpty();
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType.equals(boolean.class)) return false;
        if (returnType.equals(int.class)) return 0;
        if (returnType.equals(long.class)) return 0L;
        return null;
    }
}
