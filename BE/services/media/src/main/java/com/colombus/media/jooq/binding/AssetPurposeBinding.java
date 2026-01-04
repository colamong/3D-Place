package com.colombus.media.jooq.binding;

import java.sql.SQLException;
import java.sql.Types;
import org.jooq.Binding;
import org.jooq.BindingGetResultSetContext;
import org.jooq.BindingGetSQLInputContext;
import org.jooq.BindingGetStatementContext;
import org.jooq.BindingRegisterContext;
import org.jooq.BindingSQLContext;
import org.jooq.BindingSetSQLOutputContext;
import org.jooq.BindingSetStatementContext;
import org.jooq.Converter;
import org.jooq.impl.DSL;
import org.postgresql.util.PGobject;

import com.colombus.common.kafka.subject.model.type.AssetPurpose;

public final class AssetPurposeBinding implements Binding<Object, AssetPurpose> {

    @Override
    public Converter<Object, AssetPurpose> converter() {
        return Converter.ofNullable(
            Object.class,
            AssetPurpose.class,
            v -> (v == null) ? null : AssetPurpose.valueOf(v.toString()),
            e -> (e == null) ? null : e.name()
        );
    }

    @Override
    public void sql(BindingSQLContext<AssetPurpose> ctx) throws SQLException {
        ctx.render().visit(DSL.sql("?::asset_purpose_enum"));
    }

    @Override
    public void register(BindingRegisterContext<AssetPurpose> ctx) throws SQLException {
        ctx.statement().registerOutParameter(ctx.index(), Types.VARCHAR);
    }

    @Override
    public void set(BindingSetStatementContext<AssetPurpose> ctx) throws SQLException {
        if (ctx.value() == null) {
            ctx.statement().setNull(ctx.index(), Types.OTHER);
        } else {
            PGobject pg = new PGobject();
            pg.setType("asset_purpose_enum");
            pg.setValue(ctx.value().name());
            ctx.statement().setObject(ctx.index(), pg);
        }
    }

    @Override
    public void get(BindingGetResultSetContext<AssetPurpose> ctx) throws SQLException {
        ctx.convert(converter()).value(ctx.resultSet().getString(ctx.index()));
    }

    @Override
    public void get(BindingGetStatementContext<AssetPurpose> ctx) throws SQLException {
        ctx.convert(converter()).value(ctx.statement().getString(ctx.index()));
    }

    @Override
    public void set(BindingSetSQLOutputContext<AssetPurpose> ctx) throws SQLException {
        AssetPurpose v = ctx.value();
        ctx.output().writeString(v == null ? null : v.name());
    }

    @Override
    public void get(BindingGetSQLInputContext<AssetPurpose> ctx) throws SQLException {
        ctx.convert(converter()).value(ctx.input().readString());
    }
}