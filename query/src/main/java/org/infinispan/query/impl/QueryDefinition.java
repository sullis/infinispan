package org.infinispan.query.impl;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.hibernate.search.engine.search.query.SearchQuery;
import org.infinispan.AdvancedCache;
import org.infinispan.commons.marshall.AdvancedExternalizer;
import org.infinispan.query.dsl.embedded.impl.QueryEngine;
import org.infinispan.query.dsl.embedded.impl.SearchQueryBuilder;
import org.infinispan.query.impl.externalizers.ExternalizerIds;
import org.infinispan.util.function.SerializableFunction;

/**
 * Wraps the query to be executed in a cache represented either as a String or as a {@link SearchQuery} form together with
 * pagination and sort information.
 *
 * @since 9.2
 */
public final class QueryDefinition {

   private final SerializableFunction<AdvancedCache<?, ?>, QueryEngine<?>> queryEngineProvider;
   private final String queryString;
   private SearchQueryBuilder searchQuery;
   private int maxResults = -1;
   private int firstResult = 0;
   private long timeout = -1;
   private Set<String> sortableFields;
   private Class<?> indexedType;

   private final Map<String, Object> namedParameters = new HashMap<>();

   public QueryDefinition(String queryString, SerializableFunction<AdvancedCache<?, ?>, QueryEngine<?>> queryEngineProvider) {
      if (queryString == null) {
         throw new IllegalArgumentException("queryString cannot be null");
      }
      if (queryEngineProvider == null) {
         throw new IllegalArgumentException("queryEngineProvider cannot be null");
      }
      this.queryString = queryString;
      this.queryEngineProvider = queryEngineProvider;
   }

   public QueryDefinition(String queryString, SearchQueryBuilder searchQuery) {
      if (searchQuery == null) {
         throw new IllegalArgumentException("query cannot be null");
      }
      this.searchQuery = searchQuery;
      this.queryString = queryString;
      this.queryEngineProvider = null;
   }

   public String getQueryString() {
      return queryString;
   }

   private QueryEngine getQueryEngine(AdvancedCache<?, ?> cache) {
      if (queryEngineProvider == null) {
         throw new IllegalStateException("No query engine provider specified");
      }
      QueryEngine queryEngine = queryEngineProvider.apply(cache);
      if (queryEngine == null) {
         throw new IllegalStateException("The provider could not locate a suitable query engine");
      }
      return queryEngine;
   }

   public void initialize(AdvancedCache<?, ?> cache) {
      if (searchQuery == null) {
         QueryEngine<?> queryEngine = getQueryEngine(cache);
         searchQuery = queryEngine.buildSearchQuery(queryString, namedParameters);
         if (timeout > 0) {
            searchQuery.failAfter(timeout, TimeUnit.NANOSECONDS);
         }
      }
   }

   public SearchQueryBuilder getSearchQuery() {
      if (searchQuery == null) {
         throw new IllegalStateException("The QueryDefinition has not been initialized, make sure to call initialize(...) first");
      }
      return searchQuery;
   }

   public int getMaxResults() {
      return maxResults == -1 ? Integer.MAX_VALUE : maxResults;
   }

   public void setMaxResults(int maxResults) {
      this.maxResults = maxResults;
   }

   public void setNamedParameters(Map<String, Object> params) {
      if (params == null) {
         namedParameters.clear();
      } else {
         namedParameters.putAll(params);
      }
   }

   public void setTimeout(long timeout, TimeUnit timeUnit) {
      this.timeout = timeUnit.toNanos(timeout);
   }

   public Map<String, Object> getNamedParameters() {
      return namedParameters;
   }

   public int getFirstResult() {
      return firstResult;
   }

   public void setFirstResult(int firstResult) {
      this.firstResult = firstResult;
   }

   public Set<String> getSortableFields() {
      return sortableFields;
   }

   public void setSortableField(Set<String> sortableField) {
      this.sortableFields = sortableField;
   }

   public Class<?> getIndexedType() {
      return indexedType;
   }

   public void setIndexedType(Class<?> indexedType) {
      this.indexedType = indexedType;
   }

   public void failAfter(long timeout, TimeUnit timeUnit) {
      getSearchQuery().failAfter(timeout, timeUnit);
   }

   public static final class Externalizer implements AdvancedExternalizer<QueryDefinition> {

      @Override
      public Set<Class<? extends QueryDefinition>> getTypeClasses() {
         return Collections.singleton(QueryDefinition.class);
      }

      @Override
      public Integer getId() {
         return ExternalizerIds.QUERY_DEFINITION;
      }

      @Override
      public void writeObject(ObjectOutput output, QueryDefinition queryDefinition) throws IOException {
         output.writeUTF(queryDefinition.queryString);
         output.writeObject(queryDefinition.queryEngineProvider);
         output.writeInt(queryDefinition.firstResult);
         output.writeInt(queryDefinition.maxResults);
         output.writeObject(queryDefinition.sortableFields);
         output.writeObject(queryDefinition.indexedType);
         output.writeLong(queryDefinition.timeout);
         Map<String, Object> namedParameters = queryDefinition.namedParameters;
         int paramSize = namedParameters.size();
         output.writeShort(paramSize);
         if (paramSize != 0) {
            for (Map.Entry<String, Object> param : namedParameters.entrySet()) {
               output.writeUTF(param.getKey());
               output.writeObject(param.getValue());
            }
         }
      }

      @Override
      public QueryDefinition readObject(ObjectInput input) throws IOException, ClassNotFoundException {
         String queryString = input.readUTF();
         SerializableFunction<AdvancedCache<?, ?>, QueryEngine<?>> queryEngineProvider = (SerializableFunction<AdvancedCache<?, ?>, QueryEngine<?>>) input.readObject();
         QueryDefinition queryDefinition = new QueryDefinition(queryString, queryEngineProvider);
         queryDefinition.setFirstResult(input.readInt());
         queryDefinition.setMaxResults(input.readInt());
         Set<String> sortableField = (Set<String>) input.readObject();
         queryDefinition.setSortableField(sortableField);
         Class<?> indexedType = (Class<?>) input.readObject();
         queryDefinition.setIndexedType(indexedType);
         queryDefinition.timeout = input.readLong();
         short paramSize = input.readShort();
         if (paramSize != 0) {
            Map<String, Object> params = new HashMap<>(paramSize);
            for (int i = 0; i < paramSize; i++) {
               String key = input.readUTF();
               Object value = input.readObject();
               params.put(key, value);
            }
            queryDefinition.setNamedParameters(params);
         }
         return queryDefinition;
      }
   }
}
