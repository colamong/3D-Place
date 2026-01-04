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

import com.colombus.clan.model.type.ClanMemberStatus;

public class ClanMemberStatusBinding implements Binding<Object, ClanMemberStatus> {
    
    private static final Converter<Object, ClanMemberStatus> CONVERTER =
        new EnumConverter<>(Object.class, ClanMemberStatus.class);

    @Override public Converter<Object, ClanMemberStatus> converter() { return CONVERTER; }

    @Override public void sql(BindingSQLContext<ClanMemberStatus> ctx) throws SQLException {
        ClanMemberStatus v = ctx.value();
        if (v == null) {
            ctx.render().sql("CAST(NULL AS clan_member_status_enum)");
        } else {
            ctx.render()
               .visit(DSL.val(v.name(), String.class))
               .sql("::clan_member_status_enum");
        }
    }

    @Override public void register(BindingRegisterContext<ClanMemberStatus> ctx) throws SQLException {
        ctx.statement().registerOutParameter(ctx.index(), Types.OTHER);
    }

    @Override public void set(BindingSetStatementContext<ClanMemberStatus> ctx) throws SQLException {
        ClanMemberStatus v = ctx.value();
        if (v == null) {
            ctx.statement().setNull(ctx.index(), Types.OTHER);
            return;
        }
        var pg = new PGobject();
        pg.setType("clan_member_status_enum");
        pg.setValue(v.name());
        ctx.statement().setObject(ctx.index(), pg);
    }

    @Override public void get(BindingGetResultSetContext<ClanMemberStatus> ctx) throws SQLException {
        Object o = ctx.resultSet().getObject(ctx.index());
        ctx.value(o == null ? null : CONVERTER.from(o.toString()));
    }

    @Override public void get(BindingGetStatementContext<ClanMemberStatus> ctx) throws SQLException {
        Object o = ctx.statement().getObject(ctx.index());
        ctx.value(o == null ? null : CONVERTER.from(o.toString()));
    }

    @Override public void set(BindingSetSQLOutputContext<ClanMemberStatus> ctx) throws SQLException {
        ClanMemberStatus v = ctx.value();
        ctx.output().writeString(v == null ? null : v.name());
    }

    @Override public void get(BindingGetSQLInputContext<ClanMemberStatus> ctx) throws SQLException {
        String s = ctx.input().readString();
        ctx.value(s == null ? null : CONVERTER.from(s));
    }
}