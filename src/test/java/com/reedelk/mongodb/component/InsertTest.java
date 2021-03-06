package com.reedelk.mongodb.component;

import com.reedelk.mongodb.internal.ClientFactory;
import com.reedelk.mongodb.internal.exception.DocumentException;
import com.reedelk.runtime.api.message.Message;
import com.reedelk.runtime.api.message.MessageBuilder;
import com.reedelk.runtime.api.message.content.Pair;
import com.reedelk.runtime.api.script.dynamicvalue.DynamicObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.testcontainers.shaded.com.google.common.collect.ImmutableMap.of;

class InsertTest extends AbstractMongoDBTest {

    private Insert component = new Insert();
    private static String collectionName = "test-collection";

    @BeforeEach
    void setUp() {
        super.setUp();
        component.setConnection(connectionConfiguration);
        component.setCollection(collectionName);
        component.clientFactory = new ClientFactory();
        component.scriptService = scriptService;
    }

    @AfterEach
    void tearDown() {
        super.tearDown();
        if (component != null) {
            component.dispose();
        }
    }

    @Test
    void shouldInsertDocumentFromJsonString() {
        // Given
        String documentAsJson = "{name: 'John', surname: 'Doe', age: 23 }";
        component.setDocument(DynamicObject.from(documentAsJson));
        component.initialize();
        Message input = MessageBuilder.get(TestComponent.class).empty().build();

        // When
        Message actual = component.apply(context, input);

        // Then
        String insertedId = actual.payload();
        assertThat(insertedId).isNotNull();

        assertExistDocumentWith("{ name: 'John' }");
    }

    @Test
    void shouldInsertDocumentFromMap() {
        // Given
        Map<String, Serializable> documentMap =
                of("name", "John", "surname", "Doe", "age", 23);
        component.setDocument(DynamicObject.from(documentMap));
        component.initialize();

        Message input = MessageBuilder.get(TestComponent.class).empty().build();

        // When
        component.apply(context, input);

        // Then
        assertExistDocumentWith("{ age: 23 }");
    }

    @Test
    void shouldInsertDocumentFromPair() {
        // Given
        Pair<String, Serializable> documentPair = Pair.create("name", "John");
        component.setDocument(DynamicObject.from(documentPair));
        component.initialize();

        Message input = MessageBuilder.get(TestComponent.class).empty().build();

        // When
        component.apply(context, input);

        // Then
        assertExistDocumentWith("{ name: 'John' }");
    }

    @Test
    void shouldInsertDocumentsFromListOfJsons() {
        // Given
        String documentAsJson1 = "{name: 'John', surname: 'Doe', age: 45 }";
        String documentAsJson2 = "{name: 'Anton', surname: 'Ellis', age: 23 }";
        String documentAsJson3 = "{name: 'Olav', surname: 'Zipser', age: 65 }";
        List<String> documents = asList(documentAsJson1, documentAsJson2, documentAsJson3);

        component.setDocument(DynamicObject.from(documents));
        component.initialize();
        Message input = MessageBuilder.get(TestComponent.class).empty().build();

        // When
        Message actual = component.apply(context, input);

        // Then
        List<String> insertedIds = actual.payload();
        assertThat(insertedIds).hasSize(3);

        assertExistDocumentWith("{ name: 'John' }");
        assertExistDocumentWith("{ surname: 'Ellis' }");
        assertExistDocumentWith("{ age: 65 }");
        assertExistDocumentsWith("{ age: { $gt: 30 } }", 2);
    }

    @Test
    void shouldInsertDocumentsFromListOfMaps() {
        // Given
        Map<String, Serializable> documentAsMap1 = ImmutableMap.of("name", "John", "age", 45);
        Map<String, Serializable> documentAsMap2 = ImmutableMap.of("name", "Olav", "age", 35);
        List<Map<String, Serializable>> documents = asList(documentAsMap1, documentAsMap2);

        component.setDocument(DynamicObject.from(documents));
        component.initialize();
        Message input = MessageBuilder.get(TestComponent.class).empty().build();

        // When
        component.apply(context, input);

        // Then
        assertExistDocumentWith("{ name: 'John' }");
        assertExistDocumentWith("{ age: 35 }");
        assertExistDocumentsWith("{ age: { $gt: 35 } }", 1);
    }

    @Test
    void shouldInsertDocumentsFromListOfPairs() {
        // Given
        Pair<String, Serializable> documentAsPair1 = Pair.create("name", "John");
        Pair<String, Serializable> documentAsPair2 = Pair.create("name", "Olav");
        List<Pair<String, Serializable>> documents = asList(documentAsPair1, documentAsPair2);

        component.setDocument(DynamicObject.from(documents));
        component.initialize();
        Message input = MessageBuilder.get(TestComponent.class).empty().build();

        // When
        component.apply(context, input);

        // Then
        assertExistDocumentWith("{ name: 'John' }");
        assertExistDocumentWith("{ name: 'Olav' }");
    }

    @Test
    void shouldNotInsertDocumentsFromEmptyList() {
        // Given
        List<Pair<String, Serializable>> documents = Collections.emptyList();

        component.setDocument(DynamicObject.from(documents));
        component.initialize();
        Message input = MessageBuilder.get(TestComponent.class).empty().build();

        // When
        component.apply(context, input);

        // Then
        assertDocumentsCount(0);
    }

    @Test
    void shouldThrowExceptionWhenInsertedDocumentIsNull() {
        // Given
        component.setDocument(DynamicObject.from(null));
        component.initialize();
        Message input = MessageBuilder.get(TestComponent.class).empty().build();

        // When
        DocumentException thrown = assertThrows(DocumentException.class,
                () -> component.apply(context, input));

        // Then
        assertThat(thrown).hasMessage("Document with type=[null] is not a supported. Did you mean to update with an empty document ({}) ?");
        assertDocumentsCount(0);
    }

    @Test
    void shouldReturnIdWhenDocumentInsertedWithCustomIntIdValue() {
        // Given
        String documentAsJson = "{_id: 3, name: 'John', surname: 'Doe', age: 23 }";
        component.setDocument(DynamicObject.from(documentAsJson));
        component.initialize();
        Message input = MessageBuilder.get(TestComponent.class).empty().build();

        // When
        Message actual = component.apply(context, input);

        // Then
        Integer insertedId = actual.payload();
        assertThat(insertedId).isEqualTo(3);

        assertExistDocumentWith("{ name: 'John' }");
    }

    @Test
    void shouldReturnIdWhenDocumentInsertedWithCustomStringIdValue() {
        // Given
        String documentAsJson = "{_id: 'aabbcc', name: 'John', surname: 'Doe', age: 23 }";
        component.setDocument(DynamicObject.from(documentAsJson));
        component.initialize();
        Message input = MessageBuilder.get(TestComponent.class).empty().build();

        // When
        Message actual = component.apply(context, input);

        // Then
        String insertedId = actual.payload();
        assertThat(insertedId).isEqualTo("aabbcc");

        assertExistDocumentWith("{ name: 'John' }");
    }

    @Test
    void shouldReturnIdWhenDocumentsInsertedWithCustomIntIdValue() {
        // Given
        String documentAsJson1 = "{_id: 2, name: 'John', surname: 'Doe', age: 23 }";
        String documentAsJson2 = "{_id: 33, name: 'John', surname: 'Doe', age: 23 }";
        String documentAsJson3 = "{_id: 45, name: 'John', surname: 'Doe', age: 23 }";
        component.setDocument(DynamicObject.from(asList(documentAsJson1, documentAsJson2, documentAsJson3)));
        component.initialize();
        Message input = MessageBuilder.get(TestComponent.class).empty().build();

        // When
        Message actual = component.apply(context, input);

        // Then
        List<Integer> insertedIds = actual.payload();
        assertThat(insertedIds).containsExactly(2, 33, 45);
    }
}
