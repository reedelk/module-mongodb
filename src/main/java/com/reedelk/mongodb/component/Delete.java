package com.reedelk.mongodb.component;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.reedelk.mongodb.internal.ClientFactory;
import com.reedelk.mongodb.internal.attribute.DeleteAttributes;
import com.reedelk.mongodb.internal.commons.DocumentUtils;
import com.reedelk.mongodb.internal.commons.Unsupported;
import com.reedelk.mongodb.internal.commons.Utils;
import com.reedelk.mongodb.internal.exception.DeleteException;
import com.reedelk.runtime.api.annotation.*;
import com.reedelk.runtime.api.component.ProcessorSync;
import com.reedelk.runtime.api.converter.ConverterService;
import com.reedelk.runtime.api.flow.FlowContext;
import com.reedelk.runtime.api.message.Message;
import com.reedelk.runtime.api.message.MessageBuilder;
import com.reedelk.runtime.api.script.ScriptEngineService;
import com.reedelk.runtime.api.script.dynamicvalue.DynamicObject;
import org.bson.Document;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;

import static com.reedelk.mongodb.internal.commons.Messages.Delete.DELETE_QUERY_NULL;
import static com.reedelk.runtime.api.commons.ComponentPrecondition.Configuration.requireNotBlank;

@ModuleComponent("MongoDB Delete (One/Many)")
@ComponentOutput(
        attributes = DeleteAttributes.class,
        payload = long.class,
        description = "The number of deleted documents.")
@ComponentInput(
        payload = Object.class,
        description = "The input payload is used to evaluate the query filter expression.")
@Component(service = Delete.class, scope = ServiceScope.PROTOTYPE)
@Description("Deletes one or more documents from a database on the specified collection. " +
        "The connection configuration allows to specify host, port, database name, username and password to be used for authentication against the database. " +
        "A static or dynamic query filter can be applied to the delete operation to <b>only</b> match the documents to be deleted." +
        "The many property allows to delete <b>all</b> the documents matching the query filter (Delete Many), " +
        "otherwise just one document matching the query filter will be deleted (Delete One).")
public class Delete implements ProcessorSync {

    @DialogTitle("MongoDB Connection")
    @Property("Connection")
    @Description("MongoDB connection configuration to be used by this delete operation. " +
            "Shared configurations use the same MongoDB client.")
    private ConnectionConfiguration connection;

    @Property("Collection")
    @Hint("MyCollection")
    @Example("MyCollection")
    @Description("Sets the name of the collection to be used for the delete operation.")
    private String collection;

    @Property("Query Filter")
    @InitValue("{ _id: 2 }")
    @DefaultValue("#[message.payload()")
    @Description("Sets the query filter to be applied to the delete operation. " +
            "If no query is present the message payload will be used as query filter.")
    private DynamicObject query;

    @Property("Delete Many")
    @Example("true")
    @DefaultValue("false")
    @Description("If true deletes all the documents matching the query filter, otherwise only one will be delete.")
    private Boolean many;

    @Reference
    ConverterService converterService;
    @Reference
    ScriptEngineService scriptService;
    @Reference
    ClientFactory clientFactory;

    private MongoClient client;

    @Override
    public void initialize() {
        requireNotBlank(Delete.class, collection, "MongoDB collection must not be empty");
        this.client = clientFactory.clientByConfig(this, connection);
    }

    @Override
    public Message apply(FlowContext flowContext, Message message) {

        MongoDatabase mongoDatabase = client.getDatabase(connection.getDatabase());
        MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(collection);

        Object evaluatedQuery = Utils.evaluateOrUsePayloadWhenEmpty(query, scriptService, flowContext, message,
                () -> new DeleteException(DELETE_QUERY_NULL.format(query.value())));

        Document deleteQuery = DocumentUtils.from(converterService, evaluatedQuery, Unsupported.queryType(evaluatedQuery));

        DeleteResult deleteResult = Utils.isTrue(many) ?
                mongoCollection.deleteMany(deleteQuery) :
                mongoCollection.deleteOne(deleteQuery);

        long deletedCount = deleteResult.getDeletedCount();
        boolean acknowledged = deleteResult.wasAcknowledged();

        DeleteAttributes attributes = new DeleteAttributes(deletedCount, acknowledged, evaluatedQuery);

        return MessageBuilder.get(Delete.class)
                .withJavaObject(deletedCount)
                .attributes(attributes)
                .build();
    }

    @Override
    public void dispose() {
        clientFactory.dispose(this, connection);
        client = null;
    }

    public void setConnection(ConnectionConfiguration connection) {
        this.connection = connection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public void setQuery(DynamicObject query) {
        this.query = query;
    }

    public void setMany(Boolean many) {
        this.many = many;
    }
}
