package io.github.chakrabartyaditi2002.flakewatch.test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * JUnit Jupiter extension that truncates all public tables except flyway_schema_history
 * after each test, resetting sequences with RESTART IDENTITY CASCADE.
 */
public class TruncateTablesExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
        DataSource dataSource = applicationContext.getBean(DataSource.class);
        truncateAllTables(dataSource);
    }

    public static void truncateAllTables(DataSource dataSource) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery(
                "SELECT tablename FROM pg_tables "
                    + "WHERE schemaname = 'public' AND tablename != 'flyway_schema_history'");

            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString("tablename"));
            }

            if (!tables.isEmpty()) {
                String sql = "TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE";
                stmt.execute(sql);
            }
        }
    }
}
