package com.mesilat.gadget;

import com.atlassian.jira.ofbiz.DefaultOfBizConnectionFactory;
import org.ofbiz.core.entity.jdbc.dbtype.MsSqlDatabaseType;
import org.ofbiz.core.entity.jdbc.dbtype.Postgres73DatabaseType;
import org.ofbiz.core.entity.jdbc.dbtype.PostgresDatabaseType;
import org.ofbiz.core.entity.jdbc.dbtype.Oracle10GDatabaseType;

public class SQLFactory {
    private static final SQLGenerator generator;

    public static String getQueryOne(){
        return generator.getQueryOne();
    }
    public static String getQueryTwo(){
        return generator.getQueryTwo();
    }

    static {
        if (DefaultOfBizConnectionFactory.getInstance().getDatasourceInfo().getDatabaseTypeFromJDBCConnection() instanceof MsSqlDatabaseType){
            generator = new MSSQLGenerator(DefaultOfBizConnectionFactory.getInstance().getDatasourceInfo().getSchemaName());
        } else if (DefaultOfBizConnectionFactory.getInstance().getDatasourceInfo().getDatabaseTypeFromJDBCConnection() instanceof PostgresDatabaseType
                || DefaultOfBizConnectionFactory.getInstance().getDatasourceInfo().getDatabaseTypeFromJDBCConnection() instanceof Postgres73DatabaseType){
            generator = new PostgreSQLGenerator();
        } else if (DefaultOfBizConnectionFactory.getInstance().getDatasourceInfo().getDatabaseTypeFromJDBCConnection() instanceof Oracle10GDatabaseType) {
            generator = new OracleSQLGenerator();
        } else {
            generator = new DefaultGenerator(); // MySQL, H2
        }
    }
}