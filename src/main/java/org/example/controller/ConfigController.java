package org.example.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.aws.paramstore.AwsParamStorePropertySourceLocator;
import org.springframework.cloud.bootstrap.config.PropertySourceBootstrapProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.springframework.cloud.bootstrap.config.PropertySourceBootstrapConfiguration.BOOTSTRAP_PROPERTY_SOURCE_NAME;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

@RestController
public class ConfigController {
    @Value("${my.dummy.property}")
    private String property;

    @Autowired
    private AwsParamStorePropertySourceLocator locator;
    @Autowired
    private ConfigurableEnvironment env;


    @GetMapping("/config")
    private String getProperty(){
        return property;
    }
    
    @GetMapping(path = "/config/{application}/{profile}")
    public MutablePropertySources getEnvironment(@PathVariable("application")String application,@PathVariable("profile")String profile){

        Collection<PropertySource<?>> propertySources=locator.locateCollection(env);
        removeBootStrapProperties(env);
        insertPropertySources(env.getPropertySources(),new ArrayList<>(propertySources));
       return env.getPropertySources();
    }

    //Shamelessly copied from spring PropertySourceBootStrapConfiguration
    private void removeBootStrapProperties(ConfigurableEnvironment env) {
        MutablePropertySources propertySources = env.getPropertySources();
        for(PropertySource p:env.getPropertySources()){
            if(p.getName().startsWith("bootstrapProperties")){
                propertySources.remove(p.getName());
            }
        }
    }

    private void insertPropertySources(MutablePropertySources propertySources,
                                       List<PropertySource<?>> composite) {
        MutablePropertySources incoming = new MutablePropertySources();
        List<PropertySource<?>> reversedComposite = new ArrayList<>(composite);
        // Reverse the list so that when we call addFirst below we are maintaining the
        // same order of PropertySources
        // Wherever we call addLast we can use the order in the List since the first item
        // will end up before the rest
        Collections.reverse(reversedComposite);
        for (PropertySource<?> p : reversedComposite) {
            incoming.addFirst(p);
        }
        PropertySourceBootstrapProperties remoteProperties = new PropertySourceBootstrapProperties();
        Binder.get(environment(incoming)).bind("spring.cloud.config",
                Bindable.ofInstance(remoteProperties));
        if (!remoteProperties.isAllowOverride() || (!remoteProperties.isOverrideNone()
                && remoteProperties.isOverrideSystemProperties())) {
            for (PropertySource<?> p : reversedComposite) {
                propertySources.addFirst(p);
            }
            return;
        }
        if (remoteProperties.isOverrideNone()) {
            for (PropertySource<?> p : composite) {
                propertySources.addLast(p);
            }
            return;
        }
        if (propertySources.contains(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            if (!remoteProperties.isOverrideSystemProperties()) {
                for (PropertySource<?> p : composite) {
                    propertySources.addAfter(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, p);
                }
            }
            else {
                for (PropertySource<?> p : reversedComposite) {
                    propertySources.addBefore(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, p);
                }
            }
        }
        else {
            for (PropertySource<?> p : composite) {
                propertySources.addLast(p);
            }
        }
    }

    private Environment environment(MutablePropertySources incoming) {
        StandardEnvironment environment = new StandardEnvironment();
        for (PropertySource<?> source : environment.getPropertySources()) {
            environment.getPropertySources().remove(source.getName());
        }
        for (PropertySource<?> source : incoming) {
            environment.getPropertySources().addLast(source);
        }
        return environment;
    }
}
