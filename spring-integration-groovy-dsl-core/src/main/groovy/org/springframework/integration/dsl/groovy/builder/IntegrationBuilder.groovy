/*
 * Copyright 2002-2012 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.springframework.integration.dsl.groovy.builder

import groovy.lang.Closure;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import groovy.util.AbstractFactory
import groovy.util.FactoryBuilderSupport
import java.io.InputStream
import java.util.Collection;
import java.util.Map;

import org.apache.commons.logging.LogFactory
import org.apache.commons.logging.Log
import org.springframework.context.ApplicationContext
import org.springframework.integration.dsl.groovy.BaseIntegrationComposition
import org.springframework.integration.dsl.groovy.IntegrationConfig
import org.springframework.integration.dsl.groovy.IntegrationContext
import org.springframework.integration.dsl.groovy.Filter
import org.springframework.integration.dsl.groovy.ServiceActivator
import org.springframework.integration.dsl.groovy.Splitter
import org.springframework.integration.dsl.groovy.Transformer
import org.springframework.integration.dsl.groovy.MessageFlow
import org.codehaus.groovy.runtime.DefaultGroovyMethods

/**
 * Workaround for DefaultGroovyMethods.split()
 * @see IntegrationBuilder.dispathNodeCall()
 * @author David Turanski
 *
 */
class IntegrationBuilderCategory {
	/**
	 * Overrrides the DefaultGroovyMethods.split() with no parameters 
	 * @param self
	 * @param closure
	 * @return the result of builder invoking split with an empty string parameter
	 */
	public static Object split(Object self, Closure closure){
		self.delegate.split('')
	}
}

/**
 *
 * @author David Turanski
 *
 */
class IntegrationBuilder extends FactoryBuilderSupport {
	private static Log logger = LogFactory.getLog(IntegrationBuilder.class)
	private final IntegrationContext integrationContext;


	IntegrationBuilder() {
		super(true)
		this.integrationContext = new IntegrationContext()
	}

	IntegrationBuilder(ArrayList<String> modules) {
		this(modules as String[])
	}

	IntegrationBuilder(String... modules) {
		this()
		def moduleSupportInstances =
				getIntegrationBuilderModuleSupportInstances(modules)

		this.integrationContext.moduleSupportInstances = moduleSupportInstances

		moduleSupportInstances.each { AbstractIntegrationBuilderModuleSupport moduleSupport ->
			moduleSupport.registerBuilderFactories(this)
		}
	}



	public IntegrationContext getIntegrationContext() {
		this.integrationContext
	}

	@Override
	/*
	 * (non-Javadoc)
	 * @see groovy.util.FactoryBuilderSupport#setClosureDelegate(groovy.lang.Closure, java.lang.Object)
	 */
	protected void setClosureDelegate(Closure closure,
	Object node) {

		/*
		 * Disable builder processing of the Spring XML closure. Save for later processing 
		 * by the XML builder
		 */

		if (node.builderName == "springXml"){
			node.beanDefinitions = closure.dehydrate()
			closure.setResolveStrategy(Closure.DELEGATE_ONLY)
			closure.delegate = new ClosureEater()
		} else {
			closure.delegate = this
		}
	}

	@Override
	def registerObjectFactories() {

		registerFactory "messageFlow", new MessageFlowFactory()
		registerFactory "doWithSpringIntegration", new IntegrationContextFactory()
		/*
		 * Simple endpoints
		 */
		registerFactory "filter", new FilterFactory()
		registerFactory "transform", new TransformerFactory()
		registerFactory "handle", new ServiceActivatorFactory()
		registerFactory "bridge", new BridgeFactory()
		registerFactory "split", new SplitterFactory()
		registerFactory "aggregate", new AggregatorFactory()
		/*
		 * Router 
		 */
		registerFactory "route", new RouterCompositionFactory()
		registerFactory "when", new RouterConditionFactory()
		registerFactory "otherwise", new RouterConditionFactory()
		registerFactory "map", new ChannelMapFactory()

		/*
		 * XML Bean 
		 */
		registerFactory "springXml", new XMLBeanFactory()
		registerFactory "namespaces", new XMLNamespaceFactory()


		registerFactory "channel", new ChannelFactory()
		registerFactory "pubSubChannel", new ChannelFactory()
		registerFactory "queueChannel", new ChannelFactory()
		registerFactory "interceptor", new ChannelInterceptorFactory()
		registerFactory "wiretap", new ChannelInterceptorFactory()

		registerFactory "poll", new PollerFactory()
		registerFactory "exec", new FlowExecutionFactory()
	}

	@Override
	protected dispathNodeCall(name, args){
		use (IntegrationBuilderCategory) {
			super.dispathNodeCall(name,args)
		}
	}

	ApplicationContext createApplicationContext(ApplicationContext parentContext=null) {
		this.integrationContext.createApplicationContext(parentContext);
	}

	MessageFlow[] getMessageFlows() {
		this.integrationContext.messageFlows
	}

	public Object build(InputStream is) {
		def script = new GroovyClassLoader().parseClass(is).newInstance()
		this.build(script)
	}

	private getIntegrationBuilderModuleSupportInstances(String[] modules) {
		def instances = []
		modules?.each { module ->
			def className = "org.springframework.integration.dsl.groovy.${module}.builder.IntegrationBuilderModuleSupport"
			if (logger.isDebugEnabled()) {
				logger.debug("checking classpath for $className")
			}
			instances << Class.forName(className).newInstance()
		}
		instances
	}
}

class ClosureEater {
	def methodMissing(String name, args){

	}
}

abstract class IntegrationComponentFactory extends AbstractFactory {
	protected Log logger = LogFactory.getLog(this.class)

	protected defaultAttributes(name, value, attributes) {
		assert !(attributes.containsKey('name') && value), "$name cannot accept both a default value and a 'name' attribute"

		attributes = attributes ?: [:]
		attributes.builderName = name

		if (!attributes.containsKey('name') && value){
			attributes.name = value
		}

		attributes
	}

	public Object newInstance(FactoryBuilderSupport builder, Object name, Object value, Map attributes) throws InstantiationException, IllegalAccessException {
		if (logger.isDebugEnabled()){
			logger.debug("newInstance name: $name value:$value attr:$attributes")
		}
		
		attributes = defaultAttributes(name, value, attributes)
		def instance = doNewInstance(builder, name, value, attributes)

		def validationContext = instance.validateAttributes(attributes)
		assert !validationContext.hasErrors, validationContext.errorMessage

		instance
	}

	protected abstract doNewInstance(FactoryBuilderSupport builder, Object name, Object value, Map attributes)
}