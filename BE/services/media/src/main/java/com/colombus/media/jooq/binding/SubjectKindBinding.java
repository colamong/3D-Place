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

import com.colombus.common.kafka.subject.model.type.SubjectKind;

public final class SubjectKindBinding implements Binding<Object, SubjectKind> {

    @Override
    public Converter<Object, SubjectKind> converter() {
        return Converter.ofNullable(Object.class, SubjectKind.class,
        v -> v == null ? null : SubjectKind.valueOf(v.toString()),
        e -> e == null ? null : e.name());
    }

    // SQL 렌더링 시 enum 타입 캐스트(안전)
    @Override
    public void sql(BindingSQLContext<SubjectKind> ctx) throws SQLException {
        ctx.render().visit(DSL.sql("?::subject_kind_enum"));
    }

    @Override
    public void register(BindingRegisterContext<SubjectKind> ctx) throws SQLException {
        ctx.statement().registerOutParameter(ctx.index(), Types.VARCHAR);
    }

    // JDBC PreparedStatement 세팅 (PGobject로 정확한 enum 타입 지정)
    @Override
    public void set(BindingSetStatementContext<SubjectKind> ctx) throws SQLException {
        if (ctx.value() == null) {
            ctx.statement().setNull(ctx.index(), Types.OTHER);
        } else {
            PGobject pg = new PGobject();
            pg.setType("subject_kind_enum");
            pg.setValue(ctx.value().name());
            ctx.statement().setObject(ctx.index(), pg);
        }
    }

    // ResultSet → 값 읽기
    @Override
    public void get(BindingGetResultSetContext<SubjectKind> ctx) throws SQLException {
        ctx.convert(converter()).value(ctx.resultSet().getString(ctx.index()));
    }

    // CallableStatement → 값 읽기
    @Override
    public void get(BindingGetStatementContext<SubjectKind> ctx) throws SQLException {
        ctx.convert(converter()).value(ctx.statement().getString(ctx.index()));
    }

    // SQLOutput (UDT용) → 쓰기
    @Override
    public void set(BindingSetSQLOutputContext<SubjectKind> ctx) throws SQLException {
        SubjectKind v = ctx.value();
        ctx.output().writeString(v == null ? null : v.name());
    }

    // SQLInput (UDT용) → 읽기
    @Override
    public void get(BindingGetSQLInputContext<SubjectKind> ctx) throws SQLException {
        ctx.convert(converter()).value(ctx.input().readString());
    }
}