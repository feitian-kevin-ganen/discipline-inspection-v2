package com.group.exam.common.mybatis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL JSONB 字段 TypeHandler 基类。
 * 用法：实体类标注 @TableName(value = "t_x", autoResultMap = true)，
 * JSONB 字段标注 @TableField(typeHandler = XxxHandler.class)
 */
public abstract class AbstractJsonbTypeHandler<T> extends BaseTypeHandler<T> {

    protected static final ObjectMapper OM = new ObjectMapper();

    private final TypeReference<T> typeReference;

    protected AbstractJsonbTypeHandler(TypeReference<T> typeReference) {
        this.typeReference = typeReference;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
        try {
            // 以字符串写入：MySQL/PolarDB-X JSON 列直接接受；PostgreSQL 侧由连接串 stringtype=unspecified 隐式转 jsonb
            ps.setString(i, OM.writeValueAsString(parameter));
        } catch (Exception e) {
            throw new SQLException("JSON 序列化失败", e);
        }
    }

    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private T parse(String json) throws SQLException {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return OM.readValue(json, typeReference);
        } catch (Exception e) {
            throw new SQLException("JSONB 反序列化失败: " + json, e);
        }
    }

    /** Map<String,Object> JSONB */
    @MappedTypes(Map.class)
    public static class JsonbMapHandler extends AbstractJsonbTypeHandler<Map<String, Object>> {
        public JsonbMapHandler() {
            super(new TypeReference<>() {
            });
        }
    }

    /** List<Map<String,Object>> JSONB */
    @MappedTypes(List.class)
    public static class JsonbListMapHandler extends AbstractJsonbTypeHandler<List<Map<String, Object>>> {
        public JsonbListMapHandler() {
            super(new TypeReference<>() {
            });
        }
    }

    /** List<String> JSONB */
    @MappedTypes(List.class)
    public static class JsonbStringListHandler extends AbstractJsonbTypeHandler<List<String>> {
        public JsonbStringListHandler() {
            super(new TypeReference<>() {
            });
        }
    }

    /** List<Long> JSONB */
    @MappedTypes(List.class)
    public static class JsonbLongListHandler extends AbstractJsonbTypeHandler<List<Long>> {
        public JsonbLongListHandler() {
            super(new TypeReference<>() {
            });
        }
    }

    /** 任意对象 JSONB（反序列化为 Object，通常为 Map/List） */
    @MappedTypes(Object.class)
    public static class JsonbObjectHandler extends AbstractJsonbTypeHandler<Object> {
        public JsonbObjectHandler() {
            super(new TypeReference<>() {
            });
        }
    }
}
