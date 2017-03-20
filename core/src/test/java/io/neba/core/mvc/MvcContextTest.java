/**
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 the "License";
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package io.neba.core.mvc;

import org.apache.sling.api.SlingHttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.bind.support.WebArgumentResolver;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping;
import org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter;
import org.springframework.web.servlet.mvc.annotation.AnnotationMethodHandlerAdapter;
import org.springframework.web.servlet.mvc.annotation.AnnotationMethodHandlerExceptionResolver;
import org.springframework.web.servlet.mvc.annotation.DefaultAnnotationHandlerMapping;
import org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.springframework.web.servlet.DispatcherServlet.MULTIPART_RESOLVER_BEAN_NAME;

/**
 * @author Olaf Otto
 */
@RunWith(MockitoJUnitRunner.class)
public class MvcContextTest {
    @Mock
    private ConfigurableListableBeanFactory factory;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private ContextRefreshedEvent event;
    @Mock
    private ServletConfig servletConfig;
    @Mock
    private SlingMvcServletRequest request;
    @Mock
    private SlingHttpServletResponse response;

    private WebArgumentResolver[] registeredArgumentResolvers = new WebArgumentResolver[]{};
    private HandlerMapping handlerMapping;

    private MvcContext testee;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        doThrow(new NoSuchBeanDefinitionException("THIS IS AN EXPECTED TEST EXCEPTION"))
                .when(this.applicationContext).getBean(anyString(), isA(Class.class));
        doReturn(this.applicationContext).when(this.event).getApplicationContext();
        doReturn(this.factory).when(this.applicationContext).getAutowireCapableBeanFactory();

        Answer<Object> createMock = new Answer<Object>() {

            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                final Class<?> beanType = (Class<?>) invocation.getArguments()[0];
                return mockExistingBean(beanType);
            }
        };
        doAnswer(createMock).when(this.factory).createBean(isA(Class.class));

        this.testee = new MvcContext(this.factory);

        doReturn(mock(Enumeration.class)).when(this.request).getHeaderNames();
        doReturn(mock(ServletOutputStream.class)).when(this.response).getOutputStream();
    }

    @Test
    public void testProvisioningOfMvcInfrastructure() throws Exception {
        signalContextRefreshed();

        verifyMultipartResolverIsRegistered();
        verifyExceptionResolversAreRegistered();
        verifyHandlerAdaptersAreRegistered();
        verifyHandlerMappingsAreRegistered();
        verifyViewResolverIsRegistered();
    }

    @Test
    public void testInitializationIsIgnoredIfInfrastructureIsNotInitialized() throws Exception {
        initDispatcherServlet();
        verifyApplicationContextIsNotUsed();
    }

    @Test
    public void testInitializationIsPerformedWhenInfrastructureIsInitialized() throws Exception {
        signalContextRefreshed();
        initDispatcherServlet();
        verifyContextIsAskedFor(MULTIPART_RESOLVER_BEAN_NAME, MultipartResolver.class);
    }

    @Test
    public void testHandlingOfExistingMultipartResolver() throws Exception {
        withBeanAlreadyExistingInApplicationContext(MultipartResolver.class);

        signalContextRefreshed();
        verifyMultipartResolverIsNotRegistered();

        verifyExceptionResolversAreRegistered();
        verifyHandlerAdaptersAreRegistered();
        verifyHandlerMappingsAreRegistered();
        verifyViewResolverIsRegistered();
    }

    @Test
    public void testHandlingOfExistingExceptionResolver() throws Exception {
        withBeanAlreadyExistingInApplicationContext(HandlerExceptionResolver.class);

        signalContextRefreshed();
        verifyExceptionResolversAreNotRegistered();

        verifyMultipartResolverIsRegistered();
        verifyHandlerAdaptersAreRegistered();
        verifyHandlerMappingsAreRegistered();
        verifyViewResolverIsRegistered();
    }

    @Test
    public void testHandlingOfExistingRequestMappingHandlerAdapter() throws Exception {
        withBeanAlreadyExistingInApplicationContext(HandlerAdapter.class);
        withRequestMappingHandlerAlreadyExistingInContext();

        signalContextRefreshed();

        verifyHandlerAdaptersAreNotRegistered();
        verifyNebaArgumentResolversAreRegistered();
        verifyMultipartResolverIsRegistered();
        verifyExceptionResolversAreRegistered();
        verifyHandlerMappingsAreRegistered();
        verifyViewResolverIsRegistered();
    }

    @Test
    public void testHandlingOfExistingHandlerAdaptersWithoutRequestMappingHandlerAdapter() throws Exception {
        withBeanAlreadyExistingInApplicationContext(HandlerAdapter.class);

        signalContextRefreshed();

        verifyHandlerAdaptersAreNotRegistered();
        verifyNebaArgumentResolversAreNotRegistered();
        verifyMultipartResolverIsRegistered();
        verifyExceptionResolversAreRegistered();
        verifyHandlerMappingsAreRegistered();
        verifyViewResolverIsRegistered();
    }

    @Test
    public void testHandlingOfExistingHandlerMappings() throws Exception {
        withBeanAlreadyExistingInApplicationContext(HandlerMapping.class);

        signalContextRefreshed();

        verifyHandlerMappingsAreNotRegistered();

        verifyMultipartResolverIsRegistered();
        verifyExceptionResolversAreRegistered();
        verifyHandlerAdaptersAreRegistered();
        verifyViewResolverIsRegistered();
    }

    @Test
    public void testHandlingOfExistingViewResolver() throws Exception {
        withBeanAlreadyExistingInApplicationContext(ViewResolver.class);

        signalContextRefreshed();

        verifyViewResolverIsNotRegistered();

        verifyMultipartResolverIsRegistered();
        verifyExceptionResolversAreRegistered();
        verifyHandlerAdaptersAreRegistered();
        verifyHandlerMappingsAreRegistered();
    }

    @Test
    public void testRegistrationOfCustomArgumentResolvers() throws Exception {
        withRequestMappingHandlerCreatedOnDemand(mockRequestMappingHandler());

        signalContextRefreshed();

        verifyNebaArgumentResolversAreRegistered();
    }

    @Test
    public void testHandlingOfUnsupportedApplicationEvent() throws Exception {
        sendEvent(mock(ContextClosedEvent.class));
        verifyMvcContextIgnoresEvent();
    }

    @Test
    public void testOptionsRequestsArePassedToHandlers() throws Exception {
        withExistingHandlerMapping();
        signalContextRefreshed();
        initDispatcherServlet();

        withMethod("OPTIONS");
        service();

        verifyHandlerMappingIsUsedForRequest();
    }

    @Test
    public void testTraceRequestsArePassedToHandlers() throws Exception {
        withExistingHandlerMapping();
        signalContextRefreshed();
        initDispatcherServlet();

        withMethod("TRACE");
        // Mock expected response type to prevent the default trace behavior
        // from executing, which would require superfluous mocking
        withResponseContentType("message/http");
        service();

        verifyHandlerMappingIsUsedForRequest();
    }

    private void withResponseContentType(String type) {
        doReturn(type).when(this.response).getContentType();
    }

    private void verifyHandlerMappingIsUsedForRequest() throws Exception {
        verify(this.handlerMapping).getHandler(eq(this.request));
    }

    private void withExistingHandlerMapping() {
        this.handlerMapping = mockExistingBean(HandlerMapping.class);
    }

    private void service() throws ServletException, IOException {
        this.testee.service(this.request, this.response);
    }

    private void withMethod(String method) {
        doReturn(method).when(this.request).getMethod();
    }

    private void initDispatcherServlet() {
        this.testee.initializeDispatcherServlet(this.servletConfig);
    }

    private void sendEvent(ApplicationEvent event) {
        this.testee.onApplicationEvent(event);
    }

    private void withRequestMappingHandlerAlreadyExistingInContext() {
        doReturn(mockRequestMappingHandler()).when(this.factory).getBean(eq(AnnotationMethodHandlerAdapter.class));
    }

    private void withRequestMappingHandlerCreatedOnDemand(final AnnotationMethodHandlerAdapter handler) {
        Answer<AnnotationMethodHandlerAdapter> mockBeanCreation = new Answer<AnnotationMethodHandlerAdapter>() {
            @Override
            public AnnotationMethodHandlerAdapter answer(InvocationOnMock invocation) throws Throwable {
                doReturn(handler).when(factory).getBean(eq(AnnotationMethodHandlerAdapter.class));
                return handler;
            }
        };
        doAnswer(mockBeanCreation).when(this.factory).createBean(eq(AnnotationMethodHandlerAdapter.class));
    }

    @SuppressWarnings("unchecked")
    private AnnotationMethodHandlerAdapter mockRequestMappingHandler() {
        AnnotationMethodHandlerAdapter requestMappingHandlerAdapter = mock(AnnotationMethodHandlerAdapter.class);
        Answer<Object> retainCustomResolvers = new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                registeredArgumentResolvers = (WebArgumentResolver[]) invocation.getArguments()[0];
                return null;
            }
        };

        doAnswer(retainCustomResolvers).when(requestMappingHandlerAdapter).setCustomArgumentResolvers(Mockito.isA(WebArgumentResolver[].class));

        return requestMappingHandlerAdapter;
    }

    private void verifyNebaArgumentResolversAreRegistered() {
        assertThat(this.registeredArgumentResolvers).describedAs("The list of registered NEBA argument resolvers").hasSize(3);
        assertThat(this.registeredArgumentResolvers[0]).isInstanceOf(RequestPathInfoArgumentResolver.class);
        assertThat(this.registeredArgumentResolvers[1]).isInstanceOf(ResourceResolverArgumentResolver.class);
        assertThat(this.registeredArgumentResolvers[2]).isInstanceOf(ResourceParamArgumentResolver.class);
    }

    private void verifyNebaArgumentResolversAreNotRegistered() {
        assertThat(this.registeredArgumentResolvers).describedAs("The list of registered NEBA argument resolvers").isEmpty();
    }

    private void verifyMvcContextIgnoresEvent() {
        verifyNoMoreInteractions(this.factory);
        verifyNoMoreInteractions(this.applicationContext);
    }

    private void withBeanAlreadyExistingInApplicationContext(Class<?> type) {
        mockExistingBean(type);
    }

    private void verifyContextIsAskedFor(String beanName, Class<MultipartResolver> beanType) {
        verify(this.applicationContext).getBean(eq(beanName), eq(beanType));
    }

    private void verifyViewResolverIsRegistered() {
        verifyContextDefinesBean(NebaViewResolver.class);
    }

    private void verifyHandlerMappingsAreRegistered() {
        verifyContextDefinesBean(BeanNameUrlHandlerMapping.class);
        verifyContextDefinesBean(DefaultAnnotationHandlerMapping.class);
    }

    private void verifyHandlerMappingsAreNotRegistered() {
        verifyBeanIsNeverCreatedInFactory(BeanNameUrlHandlerMapping.class);
        verifyBeanIsNeverCreatedInFactory(DefaultAnnotationHandlerMapping.class);
    }

    private void verifyHandlerAdaptersAreRegistered() {
        verifyContextDefinesBean(HttpRequestHandlerAdapter.class);
        verifyContextDefinesBean(AnnotationMethodHandlerAdapter.class);
    }

    private void verifyHandlerAdaptersAreNotRegistered() {
        verifyBeanIsNeverCreatedInFactory(HttpRequestHandlerAdapter.class);
        verifyBeanIsNeverCreatedInFactory(AnnotationMethodHandlerAdapter.class);
    }

    private void verifyExceptionResolversAreRegistered() {
        verifyContextDefinesBean(AnnotationMethodHandlerExceptionResolver.class);
        verifyContextDefinesBean(ResponseStatusExceptionResolver.class);
        verifyContextDefinesBean(DefaultHandlerExceptionResolver.class);
    }

    private void verifyExceptionResolversAreNotRegistered() {
        Class<?> type = AnnotationMethodHandlerExceptionResolver.class;
        verifyBeanIsNeverCreatedInFactory(type);
        verifyBeanIsNeverCreatedInFactory(ResponseStatusExceptionResolver.class);
        verifyBeanIsNeverCreatedInFactory(DefaultHandlerExceptionResolver.class);
    }

    private void verifyMultipartResolverIsRegistered() {
        verifyContextDefinesBean(SlingMultipartResolver.class, MULTIPART_RESOLVER_BEAN_NAME);
    }

    private void verifyMultipartResolverIsNotRegistered() {
        verifyBeanIsNeverCreatedInFactory(SlingMultipartResolver.class);
    }

    private void verifyViewResolverIsNotRegistered() {
        verifyBeanIsNeverCreatedInFactory(NebaViewResolver.class);
    }

    private void verifyBeanIsNeverCreatedInFactory(Class<?> type) {
        verify(this.factory, never()).createBean(eq(type));
    }

    private void verifyContextDefinesBean(Class<?> type, String beanName) {
        verify(this.factory).createBean(type);
        verify(this.factory).registerSingleton(eq(beanName), isA(type));
    }

    private void verifyContextDefinesBean(Class<?> type) {
        verify(this.factory).createBean(type);
        verify(this.factory).registerSingleton(anyString(), isA(type));
    }

    private void verifyApplicationContextIsNotUsed() {
        verifyZeroInteractions(this.applicationContext);
    }

    private void signalContextRefreshed() {
        this.testee.onApplicationEvent(this.event);
    }

    private <T> T mockExistingBean(final Class<T> beanType) {
        T bean = mock(beanType, Mockito.RETURNS_MOCKS);

        Map<String, Object> matchingBeans = new HashMap<String, Object>();
        matchingBeans.put("name", bean);

        ArgumentMatcher<Class<?>> isAssignableFromBeanType = new ArgumentMatcher<Class<?>>() {
            @Override
            public boolean matches(Object argument) {
                return ((Class<?>) argument).isAssignableFrom(beanType);
            }
        };

        doReturn(matchingBeans).when(applicationContext).getBeansOfType(argThat(isAssignableFromBeanType));
        doReturn(matchingBeans).when(applicationContext).getBeansOfType(argThat(isAssignableFromBeanType), anyBoolean(), anyBoolean());
        doReturn(bean).when(applicationContext).getBean(anyString(), argThat(isAssignableFromBeanType));

        @SuppressWarnings("unchecked")
        Map<String, Object> m = mock(Map.class);
        doReturn(false).when(m).isEmpty();
        doReturn(m).when(this.factory).getBeansOfType(argThat(isAssignableFromBeanType));

        return bean;
    }
}
