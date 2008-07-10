/*
 * Copyright 2004-2008 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.binding.convert.converters;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.springframework.binding.convert.ConversionExecutor;
import org.springframework.binding.convert.ConversionService;
import org.springframework.core.GenericCollectionTypeResolver;
import org.springframework.core.JdkVersion;

/**
 * Special one-way converter that converts from a source array to a target collection. Supports the selection of an
 * "approximate" collection implementation when a target collection interface such as <code>List.class</code> is
 * specified. Supports type conversion of array elements when a concrete parameterized collection class is provided,
 * such as <code>IntegerList<Integer>.class</code>.
 * 
 * Note that type erasure prevents arbitrary access to generic collection element type information at runtime,
 * preventing the ability to convert elements for collections declared as properties.
 * 
 * Mainly used internally by {@link ConversionService} implementations.
 * 
 * @author Keith Donald
 */
public class ArrayToCollection implements TwoWayConverter {

	private ConversionService conversionService;

	public ArrayToCollection(ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	public Class getSourceClass() {
		return Object[].class;
	}

	public Class getTargetClass() {
		return Collection.class;
	}

	public Object convertSourceToTargetClass(Object source, Class targetClass) throws Exception {
		if (source == null) {
			return null;
		}
		Class collectionImplClass;
		if (targetClass.isInterface()) {
			if (List.class.equals(targetClass)) {
				collectionImplClass = ArrayList.class;
			} else if (Set.class.equals(targetClass)) {
				collectionImplClass = LinkedHashSet.class;
			} else if (SortedSet.class.equals(targetClass)) {
				collectionImplClass = TreeSet.class;
			} else {
				throw new IllegalArgumentException("Unsupported collection interface [" + targetClass.getName() + "]");
			}
		} else {
			collectionImplClass = targetClass;
		}
		Constructor constructor = collectionImplClass.getConstructor(null);
		ConversionExecutor converter;
		if (JdkVersion.isAtLeastJava15()) {
			Class elementType = GenericCollectionTypeResolver.getCollectionType(targetClass);
			if (elementType != null) {
				Class componentType = source.getClass().getComponentType();
				converter = conversionService.getConversionExecutor(componentType, elementType);
			} else {
				converter = null;
			}
		} else {
			converter = null;
		}
		Collection collection = (Collection) constructor.newInstance(null);
		int length = Array.getLength(source);
		if (converter != null) {
			for (int i = 0; i < length; i++) {
				Object value = Array.get(source, i);
				value = converter.execute(value);
				collection.add(value);
			}
		} else {
			for (int i = 0; i < length; i++) {
				Object value = Array.get(source, i);
				collection.add(value);
			}
		}
		return collection;
	}

	public Object convertTargetToSourceClass(Object target, Class sourceClass) throws Exception {
		if (target == null) {
			return null;
		}
		Collection collection = (Collection) target;
		Object array = Array.newInstance(sourceClass.getComponentType(), collection.size());
		int i = 0;
		for (Iterator it = collection.iterator(); it.hasNext(); i++) {
			Object value = it.next();
			if (value != null) {
				ConversionExecutor converter = conversionService.getConversionExecutor(value.getClass(), sourceClass
						.getComponentType());
				value = converter.execute(value);
			}
			Array.set(array, i, value);
		}
		return array;
	}
}