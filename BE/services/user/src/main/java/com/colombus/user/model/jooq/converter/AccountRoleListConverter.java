package com.colombus.user.model.jooq.converter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.jooq.Converter;
import com.colombus.user.model.type.AccountRole;

public final class AccountRoleListConverter implements Converter<Object[], List<AccountRole>> {
    @Override public Class<Object[]> fromType() { return Object[].class; }
    @Override @SuppressWarnings("unchecked") public Class<List<AccountRole>> toType() { return (Class) List.class; }

    @Override 
    public List<AccountRole> from(Object[] databaseObject) {
        if (databaseObject == null) return null;

        return Arrays.stream(databaseObject)
            .map(obj -> AccountRole.valueOf(obj.toString()))
            .collect(Collectors.toList());
    }

    @Override
    public Object[] to(List<AccountRole> userObject) {
        if (userObject == null) return null;
        
        return userObject.stream()
            .map(Enum::name)
            .toArray(Object[]::new);
    }
}