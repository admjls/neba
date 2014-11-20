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

package io.neba.core.resourcemodels.metadata;

import static org.fest.assertions.Assertions.assertThat;
import static org.springframework.util.ReflectionUtils.findMethod;

import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;

import io.neba.core.resourcemodels.mapping.testmodels.TestResourceModelWithLifecycleCallbacks;

/**
 * @author Olaf Otto
 */
public class MethodMetadataTest {
	private Class<?> modelType;
	
	private MethodMetaData testee;

	@Before
	public void prepare() {
		withModelType(TestResourceModelWithLifecycleCallbacks.class);
	}

	@Test
	public void testDetectionOfPreMappingAnnotation() throws Exception {
		createMetadataForTestModelMethodWithName("beforeMapping");
		assertMappingIsPreMappingCallback();
		assertMappingIsNotPostMappingCallback();
	}

	@Test
	public void testDetectionOfPostMappingAnnotation() throws Exception {
		createMetadataForTestModelMethodWithName("afterMapping");
		assertMappingIsPostMappingCallback();
		assertMappingIsNotPreMappingCallback();
	}

    @Test
    public void testHashCodeAndEquals() throws Exception {
        Method method = findMethod(this.modelType, "beforeMapping");

        MethodMetaData one = new MethodMetaData(method);
        MethodMetaData two = new MethodMetaData(method);

        assertThat(one.hashCode()).isEqualTo(two.hashCode());
        assertThat(one).isEqualTo(two);
        assertThat(two).isEqualTo(one);

        method = findMethod(this.modelType, "afterMapping");
        two = new MethodMetaData(method);

        assertThat(one.hashCode()).isNotEqualTo(two.hashCode());
        assertThat(one).isNotEqualTo(two);
        assertThat(two).isNotEqualTo(one);
    }

    private void assertMappingIsPostMappingCallback() {
		assertThat(this.testee.isPostMappingCallback()).isTrue();
	}

	private void assertMappingIsNotPostMappingCallback() {
		assertThat(this.testee.isPostMappingCallback()).isFalse();
	}

	private void assertMappingIsPreMappingCallback() {
		assertThat(this.testee.isPreMappingCallback()).isTrue();
	}

	private void assertMappingIsNotPreMappingCallback() {
		assertThat(this.testee.isPreMappingCallback()).isFalse();
	}

	private void createMetadataForTestModelMethodWithName(String name) {
		Method method = findMethod(this.modelType, name);
		this.testee = new MethodMetaData(method);
	}

	private void withModelType(Class<?> type) {
		this.modelType = type;
	}
}