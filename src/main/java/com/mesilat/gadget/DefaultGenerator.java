package com.mesilat.gadget;

import java.util.ResourceBundle;

public class DefaultGenerator implements SQLGenerator {
    protected String sql1;
    protected String sql2;
    protected String sql3;
    protected String sql4;

    @Override
    public String getQueryOne() {
        return sql1;
    }
    @Override
    public String getQueryTwo() {
        return sql2;
    }
    @Override
    public String getQueryTotalWorklog(){
        return sql3;
    }
    @Override
    public String getQueryReport() {
        return sql4;
    }

    public DefaultGenerator(){
        this("default");
    }
    public DefaultGenerator(String bundleName){
        ResourceBundle bundle = ResourceBundle.getBundle(bundleName);
        sql1 = bundle.getString("com.mesilat.week-load.sql1");
        sql2 = bundle.getString("com.mesilat.week-load.sql2");
        sql3 = bundle.getString("com.mesilat.week-load.sql3");
        sql4 = bundle.getString("com.mesilat.week-load.sql4");
    }
}