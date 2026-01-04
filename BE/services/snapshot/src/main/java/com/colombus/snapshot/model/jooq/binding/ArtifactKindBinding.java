package com.colombus.snapshot.model.jooq.binding;

import com.colombus.snapshot.model.type.ArtifactKind;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.EnumConverter;
import org.postgresql.util.PGobject;

import java.sql.SQLException;
import java.sql.Types;

public class ArtifactKindBinding implements Binding<Object, ArtifactKind> {

    private static final Converter<Object, ArtifactKind> CONVERTER =
        new EnumConverter<>(Object.class, ArtifactKind.class);

    @Override public Converter<Object, ArtifactKind> converter() { return CONVERTER; }

    @Override public void sql(BindingSQLContext<ArtifactKind> ctx) throws SQLException {
        ArtifactKind v = ctx.value();
        if (v == null) {
            ctx.render().sql("CAST(NULL AS artifact_kind_enum)");
        } else {
            ctx.render()
               .visit(DSL.val(v.getDbValue(), String.class))
               .sql("::artifact_kind_enum");
        }
    }

    @Override public void register(BindingRegisterContext<ArtifactKind> ctx) throws SQLException {
        ctx.statement().registerOutParameter(ctx.index(), Types.OTHER);
    }

    @Override public void set(BindingSetStatementContext<ArtifactKind> ctx) throws SQLException {
        ArtifactKind v = ctx.value();
        if (v == null) {
            ctx.statement().setNull(ctx.index(), Types.OTHER);
            return;
        }
        var pg = new PGobject();
        pg.setType("artifact_kind_enum");
        pg.setValue(v.name());
        ctx.statement().setObject(ctx.index(), pg);
    }

    @Override public void get(BindingGetResultSetContext<ArtifactKind> ctx) throws SQLException {
        String dbValue = ctx.resultSet().getString(ctx.index());
        ctx.convert(converter()).value(ArtifactKind.fromDbValue(dbValue));
    }

    @Override public void get(BindingGetStatementContext<ArtifactKind> ctx) throws SQLException {
        String dbValue = ctx.statement().getString(ctx.index());
        ctx.convert(converter()).value(ArtifactKind.fromDbValue(dbValue));
    }

    @Override public void set(BindingSetSQLOutputContext<ArtifactKind> ctx) throws SQLException {
        ArtifactKind v = ctx.value();
        ctx.output().writeString(v == null ? null : v.name());
    }

    @Override public void get(BindingGetSQLInputContext<ArtifactKind> ctx) throws SQLException {
        String s = ctx.input().readString();
        ctx.value(s == null ? null : CONVERTER.from(s));
    }
}