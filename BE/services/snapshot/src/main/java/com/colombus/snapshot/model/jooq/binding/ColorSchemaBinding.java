package com.colombus.snapshot.model.jooq.binding;

import com.colombus.snapshot.model.type.ColorSchema;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.EnumConverter;
import org.postgresql.util.PGobject;

import java.sql.SQLException;
import java.sql.Types;

public class ColorSchemaBinding implements Binding<Object, ColorSchema> {

    private static final Converter<Object, ColorSchema> CONVERTER =
        new EnumConverter<>(Object.class, ColorSchema.class);

    @Override public Converter<Object, ColorSchema> converter() { return CONVERTER; }

    @Override public void sql(BindingSQLContext<ColorSchema> ctx) throws SQLException {
        ColorSchema v = ctx.value();
        if (v == null) {
            ctx.render().sql("CAST(NULL AS color_schema_enum)");
        } else {
            ctx.render()
               .visit(DSL.val(v.name(), String.class))
               .sql("::color_schema_enum");
        }
    }

    @Override public void register(BindingRegisterContext<ColorSchema> ctx) throws SQLException {
        ctx.statement().registerOutParameter(ctx.index(), Types.OTHER);
    }

    @Override public void set(BindingSetStatementContext<ColorSchema> ctx) throws SQLException {
        ColorSchema v = ctx.value();
        if (v == null) {
            ctx.statement().setNull(ctx.index(), Types.OTHER);
            return;
        }
        var pg = new PGobject();
        pg.setType("color_schema_enum");
        pg.setValue(v.name());
        ctx.statement().setObject(ctx.index(), pg);
    }

    @Override public void get(BindingGetResultSetContext<ColorSchema> ctx) throws SQLException {
        Object o = ctx.resultSet().getObject(ctx.index());
        ctx.value(o == null ? null : CONVERTER.from(o.toString()));
    }

    @Override public void get(BindingGetStatementContext<ColorSchema> ctx) throws SQLException {
        Object o = ctx.statement().getObject(ctx.index());
        ctx.value(o == null ? null : CONVERTER.from(o.toString()));
    }

    @Override public void set(BindingSetSQLOutputContext<ColorSchema> ctx) throws SQLException {
        ColorSchema v = ctx.value();
        ctx.output().writeString(v == null ? null : v.name());
    }

    @Override public void get(BindingGetSQLInputContext<ColorSchema> ctx) throws SQLException {
        String s = ctx.input().readString();
        ctx.value(s == null ? null : CONVERTER.from(s));
    }
}