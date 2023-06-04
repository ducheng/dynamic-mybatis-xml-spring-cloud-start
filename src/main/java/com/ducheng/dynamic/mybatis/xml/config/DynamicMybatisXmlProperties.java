package com.ducheng.dynamic.mybatis.xml.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@ConfigurationProperties(prefix = "spring.cloud.dynamic.mybatis.xml")
@RefreshScope
public class DynamicMybatisXmlProperties {

    private String dataId;

    private String xmlName;

    private Boolean enable = true;

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public String getXmlName() {
        return xmlName;
    }

    public void setXmlName(String xmlName) {
        this.xmlName = xmlName;
    }

    public Boolean getEnable() {
        return enable;
    }

    public void setEnable(Boolean enable) {
        this.enable = enable;
    }

    public DynamicMybatisXmlProperties() {
    }

    public DynamicMybatisXmlProperties(String dataId, String xmlName, Boolean enable) {
        this.dataId = dataId;
        this.xmlName = xmlName;
        this.enable = enable;
    }
}
