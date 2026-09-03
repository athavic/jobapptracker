package com.jobtracker.tenancy.rls;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Hands the two multi-tenancy components to Hibernate.
 *
 * <p>Spring Boot will wire beans of these types on its own in most versions,
 * but "in most versions" is not a property to depend on for the class that
 * decides whether tenants can read each other. Setting them explicitly means an
 * upgrade that changes the auto-configuration fails at startup, where Hibernate
 * complains about a missing provider, rather than silently reverting to
 * single-tenant behaviour - which would leave every connection unscoped and, by
 * V8's design, reading nothing at all.
 *
 * <p>The instances are passed directly rather than as class names so they are
 * the Spring beans, with their dependencies injected. Hibernate will happily
 * instantiate a class name itself, and the result would have a null DataSource.
 */
@Configuration
class RowLevelSecurityConfig implements HibernatePropertiesCustomizer {

    private final RowLevelSecurityConnectionProvider connectionProvider;
    private final WorkspaceTenantResolver tenantResolver;

    RowLevelSecurityConfig(RowLevelSecurityConnectionProvider connectionProvider,
                           WorkspaceTenantResolver tenantResolver) {
        this.connectionProvider = connectionProvider;
        this.tenantResolver = tenantResolver;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
    }
}
