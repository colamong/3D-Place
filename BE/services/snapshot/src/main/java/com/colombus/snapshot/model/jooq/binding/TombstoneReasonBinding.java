package com.colombus.snapshot.model.jooq.binding;

import com.colombus.snapshot.model.type.TombstoneReason;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.EnumConverter;
import org.postgresql.util.PGobject;

import java.sql.SQLException;
import java.sql.Types;

public class TombstoneReasonBinding implements Binding<Object, TombstoneReason> {

    private static final Converter<Object, TombstoneReason> CONVERTER =
        new EnumConverter<>(Object.class, TombstoneReason.class);

    @Override public Converter<Object, TombstoneReason> converter() { return CONVERTER; }

    @Override public void sql(BindingSQLContext<TombstoneReason> ctx) throws SQLException {
        TombstoneReason v = ctx.value();
        if (v == null) {
            ctx.render().sql("CAST(NULL AS tombstone_reason_enum)");
        } else {
            ctx.render()
               .visit(DSL.val(v.name(), String.class))
               .sql("::tombstone_reason_enum");
        }
    }

    @Override public void register(BindingRegisterContext<TombstoneReason> ctx) throws SQLException {
        ctx.statement().registerOutParameter(ctx.index(), Types.OTHER);
    }

    @Override public void set(BindingSetStatementContext<TombstoneReason> ctx) throws SQLException {
        TombstoneReason v = ctx.value();
        if (v == null) {
            ctx.statement().setNull(ctx.index(), Types.OTHER);
            return;
        }
        var pg = new PGobject();
        pg.setType("tombstone_reason_enum");
        pg.setValue(v.name());
        ctx.statement().setObject(ctx.index(), pg);
    }

    @Override public void get(BindingGetResultSetContext<TombstoneReason> ctx) throws SQLException {
        Object o = ctx.resultSet().getObject(ctx.index());
        ctx.value(o == null ? null : CONVERTER.from(o.toString()));
    }

    @Override public void get(BindingGetStatementContext<TombstoneReason> ctx) throws SQLException {
        Object o = ctx.statement().getObject(ctx.index());
        ctx.value(o == null ? null : CONVERTER.from(o.toString()));
    }

    @Override public void set(BindingSetSQLOutputContext<TombstoneReason> ctx) throws SQLException {
        TombstoneReason v = ctx.value();
        ctx.output().writeString(v == null ? null : v.name());
    }

    @Override public void get(BindingGetSQLInputContext<TombstoneReason> ctx) throws SQLException {
        String s = ctx.input().readString();
        ctx.value(s == null ? null : CONVERTER.from(s));
    }
}