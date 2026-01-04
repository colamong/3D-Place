package com.colombus.snapshot.model.jooq.binding;

import com.colombus.snapshot.model.type.CompactionStatus;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.EnumConverter;
import org.postgresql.util.PGobject;

import java.sql.SQLException;
import java.sql.Types;

public class CompactionStatusBinding implements Binding<Object, CompactionStatus> {

    private static final Converter<Object, CompactionStatus> CONVERTER =
        new EnumConverter<>(Object.class, CompactionStatus.class);

    @Override public Converter<Object, CompactionStatus> converter() { return CONVERTER; }

    @Override public void sql(BindingSQLContext<CompactionStatus> ctx) throws SQLException {
        CompactionStatus v = ctx.value();
        if (v == null) {
            ctx.render().sql("CAST(NULL AS compaction_status_enum)");
        } else {
            ctx.render()
               .visit(DSL.val(v.name(), String.class))
               .sql("::compaction_status_enum");
        }
    }

    @Override public void register(BindingRegisterContext<CompactionStatus> ctx) throws SQLException {
        ctx.statement().registerOutParameter(ctx.index(), Types.OTHER);
    }

    @Override public void set(BindingSetStatementContext<CompactionStatus> ctx) throws SQLException {
        CompactionStatus v = ctx.value();
        if (v == null) {
            ctx.statement().setNull(ctx.index(), Types.OTHER);
            return;
        }
        var pg = new PGobject();
        pg.setType("compaction_status_enum");
        pg.setValue(v.name());
        ctx.statement().setObject(ctx.index(), pg);
    }

    @Override public void get(BindingGetResultSetContext<CompactionStatus> ctx) throws SQLException {
        Object o = ctx.resultSet().getObject(ctx.index());
        ctx.value(o == null ? null : CONVERTER.from(o.toString()));
    }

    @Override public void get(BindingGetStatementContext<CompactionStatus> ctx) throws SQLException {
        Object o = ctx.statement().getObject(ctx.index());
        ctx.value(o == null ? null : CONVERTER.from(o.toString()));
    }

    @Override public void set(BindingSetSQLOutputContext<CompactionStatus> ctx) throws SQLException {
        CompactionStatus v = ctx.value();
        ctx.output().writeString(v == null ? null : v.name());
    }

    @Override public void get(BindingGetSQLInputContext<CompactionStatus> ctx) throws SQLException {
        String s = ctx.input().readString();
        ctx.value(s == null ? null : CONVERTER.from(s));
    }
}