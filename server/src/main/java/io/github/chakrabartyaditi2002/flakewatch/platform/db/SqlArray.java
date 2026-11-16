package io.github.chakrabartyaditi2002.flakewatch.platform.db;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.springframework.jdbc.core.SqlTypeValue;

/**
 * A PostgreSQL array as a query parameter, for {@code = any(:ids)} and bulk {@code unnest(...)} inserts.
 * Elements may be null.
 */
public final class SqlArray implements SqlTypeValue {

    private final String elementType;
    private final Object[] elements;

    private SqlArray(String elementType, Object[] elements) {
        this.elementType = elementType;
        this.elements = elements.clone();
    }

    /** @param elementType a PostgreSQL type name such as {@code bigint}, {@code text} or {@code boolean} */
    public static SqlArray of(String elementType, Object[] elements) {
        return new SqlArray(elementType, elements);
    }

    @Override
    public void setTypeValue(PreparedStatement ps, int paramIndex, int sqlType, String typeName) throws SQLException {
        ps.setArray(paramIndex, ps.getConnection().createArrayOf(elementType, elements));
    }
}
