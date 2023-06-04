package com.ducheng.dynamic.mybatis.xml.config;


import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.builder.xml.XMLMapperEntityResolver;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 *  nacos 的监听器，监听nacos 的变化
 */

@Slf4j
@org.springframework.context.annotation.Configuration
public class DynamicMybatisXmlNacosRefresherConfig implements ApplicationListener<EnvironmentChangeEvent> {

    @Autowired
    private NacosConfigProperties nacosConfigProperties;

    @Autowired
    private DynamicMybatisXmlProperties  dynamicMybatisXmlProperties;

    @Autowired
    private List<SqlSessionFactory> sqlSessionFactories;

    @Autowired
    private MybatisProperties mybatisProperties;

    @Override
    public void onApplicationEvent(EnvironmentChangeEvent environmentChangeEvent) {

        if (dynamicMybatisXmlProperties.getEnable()) {
            ConfigService configService = null;
            try {
                configService = NacosFactory.createConfigService(nacosConfigProperties.assembleConfigServiceProperties());
                String config = configService.getConfig(dynamicMybatisXmlProperties.getDataId(), nacosConfigProperties.getGroup(),1000);
                byte[] bytes  =  config.getBytes(StandardCharsets.UTF_8);
                InputStream input = new ByteArrayInputStream(bytes);

                String[] mapperLocations = mybatisProperties.getMapperLocations();
                List<Resource> resources = resolveMapperLocations(Arrays.asList(mapperLocations));
                String changeName = "BookMapper.xml";

                for (SqlSessionFactory sqlSessionFactory : sqlSessionFactories) {
                    try {
                        Configuration targetConfiguration = sqlSessionFactory.getConfiguration();
                        Class<?> tClass = targetConfiguration.getClass(), aClass = targetConfiguration.getClass();
                        //兼容mybatisplus
                        if (targetConfiguration.getClass().getSimpleName().equals("MybatisConfiguration")) {
                            aClass = Configuration.class;
                        }
                        Set<String> loadedResources = (Set<String>)getFieldValue(targetConfiguration,aClass,"loadedResources");
                        loadedResources.clear();
                        Map<String, ResultMap> resultMaps = (Map<String, ResultMap>) getFieldValue(targetConfiguration,tClass, "resultMaps");
                        Map<String, XNode> sqlFragmentsMaps = (Map<String, XNode>) getFieldValue(targetConfiguration, tClass,"sqlFragments");
                        Map<String, MappedStatement> mappedStatementMaps = (Map<String, MappedStatement>) getFieldValue(targetConfiguration, tClass,"mappedStatements");
                        for (Resource mapperLocation : resources) {
                            if (!changeName.equals(mapperLocation.getFile().getName())) {
                                continue;
                            }
                            XPathParser parser = new XPathParser(input, true, targetConfiguration.getVariables(), new XMLMapperEntityResolver());
                            XNode mapperXnode = parser.evalNode("/mapper");
                            List<XNode> resultMapNodes = mapperXnode.evalNodes("/mapper/resultMap");
                            String namespace = mapperXnode.getStringAttribute("namespace");
                            for (XNode xNode : resultMapNodes) {
                                String id = xNode.getStringAttribute("id", xNode.getValueBasedIdentifier());
                                resultMaps.remove(namespace + "." + id);
                            }

                            List<XNode> sqlNodes = mapperXnode.evalNodes("/mapper/sql");
                            for (XNode sqlNode : sqlNodes) {
                                String id = sqlNode.getStringAttribute("id", sqlNode.getValueBasedIdentifier());
                                sqlFragmentsMaps.remove(namespace + "." + id);
                            }

                            List<XNode> msNodes = mapperXnode.evalNodes("select|insert|update|delete");
                            for (XNode msNode : msNodes) {
                                String id = msNode.getStringAttribute("id", msNode.getValueBasedIdentifier());
                                mappedStatementMaps.remove(namespace + "." + id);
                            }
                            try {
                                XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(parser,
                                        targetConfiguration, new String(bytes,StandardCharsets.UTF_8), targetConfiguration.getSqlFragments());
                                xmlMapperBuilder.parse();
                            } catch (Exception e) {
                                log.error(e.getMessage(), e);
                            }
                            log.info("Parsed mapper file: '" + new String(bytes,StandardCharsets.UTF_8) + "'");
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }
                log.info("打印参数：{}",config);
            } catch (NacosException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * Use reflection to get the field value.
     *
     * @param targetConfiguration
     * @param aClass
     * @param filed
     * @return
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    private static Object getFieldValue(Configuration targetConfiguration, Class<?> aClass,
                                        String filed) throws NoSuchFieldException, IllegalAccessException {
        Field resultMapsField = aClass.getDeclaredField(filed);
        resultMapsField.setAccessible(true);
        return resultMapsField.get(targetConfiguration);
    }


    /**
     * 根据mapper路径转成resource
     * @param mapperLocations
     * @return
     */
    public List<Resource> resolveMapperLocations(List<String> mapperLocations) {
        ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
        List<Resource> resources = new ArrayList();
        if (!CollectionUtils.isEmpty(mapperLocations)) {
            for (String mapperLocation : mapperLocations) {
                try {
                    Resource[] mappers = resourceResolver.getResources(mapperLocation);
                    resources.addAll(Arrays.asList(mappers));
                } catch (IOException e) {
                    log.error("Get myBatis resources happened exception", e);
                }
            }
        }
        return resources;
    }
}
