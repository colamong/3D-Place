package com.colombus.clan.model.jooq.binding;

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
import org.jooq.impl.EnumConverter;
import org.postgresql.util.PGobject;

import com.colombus.clan.model.type.ClanJoinPolicy;

public class ClanJoinPolicyBinding implements Binding<Object, ClanJoinPolicy> {
    
    private static final Converter<Object, ClanJoinPolicy> CONVERTER =
        new EnumConverter<>(Object.class, ClanJoinPolicy.class);

    @Override public Converter<Object, ClanJoinPolicy> converter() { return CONVERTER; }

    @Override public void sql(BindingSQLContext<ClanJoinPolicy> ctx) throws SQLException {
        ClanJoinPolicy v = ctx.value();
        if (v == null) {
            ctx.render().sql("CAST(NULL AS clan_join_policy_enum)");
        } else {
            ctx.render()
               .visit(DSL.val(v.name(), String.class))
               .sql("::clan_join_policy_enum");
        }
    }

    @Override public void register(BindingRegisterContext<ClanJoinPolicy> ctx) throws SQLException {
        ctx.statement().registerOutParameter(ctx.index(), Types.OTHER);
    }

    @Override public void set(BindingSetStatementContext<ClanJoinPolicy> ctx) throws SQLException {
        ClanJoinPolicy v = ctx.value();
        if (v == null) {
            ctx.statement().setNull(ctx.index(), Types.OTHER);
            return;
        }
        var pg = new PGobject();
        pg.setType("clan_join_policy_enum");
        pg.setValue(v.name());
        ctx.statement().setObject(ctx.index(), pg);
    }

    @Override public void get(BindingGetResultSetContext<ClanJoinPolicy> ctx) throws SQLException {
        Object o = ctx.resultSet().getObject(ctx.index());
        ctx.value(o == null ? null : CONVERTER.from(o.toString()));
    }

    @Override public void get(BindingGetStatementContext<ClanJoinPolicy> ctx) throws SQLException {
        Object o = ctx.statement().getObject(ctx.index());
        ctx.value(o == null ? null : CONVERTER.from(o.toString()));
    }

    @Override public void set(BindingSetSQLOutputContext<ClanJoinPolicy> ctx) throws SQLException {
        ClanJoinPolicy v = ctx.value();
        ctx.output().writeString(v == null ? null : v.name());
    }

    @Override public void get(BindingGetSQLInputContext<ClanJoinPolicy> ctx) throws SQLException {
        String s = ctx.input().readString();
        ctx.value(s == null ? null : CONVERTER.from(s));
    }
}