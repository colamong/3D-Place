package com.colombus.snapshot.model.jooq.binding;

import com.colombus.snapshot.model.type.SnapshotKind;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.EnumConverter;
import org.postgresql.util.PGobject;

import java.sql.SQLException;
import java.sql.Types;

public class SnapshotKindBinding implements Binding<Object, SnapshotKind> {

    private static final Converter<Object, SnapshotKind> CONVERTER =
        new EnumConverter<>(Object.class, SnapshotKind.class);

    @Override public Converter<Object, SnapshotKind> converter() { return CONVERTER; }

    @Override public void sql(BindingSQLContext<SnapshotKind> ctx) throws SQLException {
        SnapshotKind v = ctx.value();
        if (v == null) {
            ctx.render().sql("CAST(NULL AS snapshot_kind_enum)");
        } else {
            ctx.render()
               .visit(DSL.val(v.getDbValue(), String.class))
               .sql("::snapshot_kind_enum");
        }
    }

    @Override public void register(BindingRegisterContext<SnapshotKind> ctx) throws SQLException {
        ctx.statement().registerOutParameter(ctx.index(), Types.OTHER);
    }

    @Override public void set(BindingSetStatementContext<SnapshotKind> ctx) throws SQLException {
        SnapshotKind v = ctx.value();
        if (v == null) {
            ctx.statement().setNull(ctx.index(), Types.OTHER);
            return;
        }
        var pg = new PGobject();
        pg.setType("snapshot_kind_enum");
        pg.setValue(v.name());
        ctx.statement().setObject(ctx.index(), pg);
    }

    @Override public void get(BindingGetResultSetContext<SnapshotKind> ctx) throws SQLException {
        String dbValue = ctx.resultSet().getString(ctx.index());
        ctx.value(dbValue == null ? null : SnapshotKind.fromDbValue(dbValue));
    }

    @Override public void get(BindingGetStatementContext<SnapshotKind> ctx) throws SQLException {
        Object o = ctx.statement().getObject(ctx.index());
        ctx.value(o == null ? null : SnapshotKind.fromDbValue(o.toString()));
    }

    @Override public void set(BindingSetSQLOutputContext<SnapshotKind> ctx) throws SQLException {
        SnapshotKind v = ctx.value();
        ctx.output().writeString(v == null ? null : v.name());
    }

    @Override public void get(BindingGetSQLInputContext<SnapshotKind> ctx) throws SQLException {
        String s = ctx.input().readString();
        ctx.value(s == null ? null : CONVERTER.from(s));
    }
}