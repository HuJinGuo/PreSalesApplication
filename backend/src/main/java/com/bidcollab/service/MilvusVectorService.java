package com.bidcollab.service;

import com.bidcollab.config.VectorStoreProperties;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

@Service
public class MilvusVectorService {
  private final VectorStoreProperties properties;
  private MilvusServiceClient client;

  public MilvusVectorService(VectorStoreProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  public void init() {
    VectorStoreProperties.Milvus conf = properties.getMilvus();
    this.client = new MilvusServiceClient(ConnectParam.newBuilder()
        .withHost(conf.getHost())
        .withPort(conf.getPort())
        .build());
    ensureCollection();
  }

  @PreDestroy
  public void destroy() {
    if (client != null) {
      client.close();
    }
  }

  public void upsert(Long kbId, Long docId, List<ChunkVectorRecord> vectors) {
    if (vectors == null || vectors.isEmpty()) {
      return;
    }
    deleteByDocumentId(docId);
    VectorStoreProperties.Milvus conf = properties.getMilvus();
    List<Long> ids = vectors.stream().map(ChunkVectorRecord::getChunkId).toList();
    List<Long> kbIds = vectors.stream().map(v -> kbId).toList();
    List<Long> docIds = vectors.stream().map(v -> docId).toList();
    List<Integer> chunkIndexes = vectors.stream().map(ChunkVectorRecord::getChunkIndex).toList();
    List<List<Float>> embeddings = vectors.stream()
        .map(v -> v.getVector().stream().map(Double::floatValue).toList())
        .toList();

    List<InsertParam.Field> fields = new ArrayList<>();
    fields.add(new InsertParam.Field("id", ids));
    fields.add(new InsertParam.Field("kb_id", kbIds));
    fields.add(new InsertParam.Field("doc_id", docIds));
    fields.add(new InsertParam.Field("chunk_index", chunkIndexes));
    fields.add(new InsertParam.Field("vector", embeddings));

    R<MutationResult> result = client.insert(InsertParam.newBuilder()
        .withCollectionName(conf.getCollection())
        .withFields(fields)
        .build());
    check(result, "insert vectors");
    client.flush(FlushParam.newBuilder()
        .withCollectionNames(List.of(conf.getCollection()))
        .build());
    client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(conf.getCollection()).build());
  }

  public void deleteByDocumentId(Long docId) {
    if (docId == null) {
      return;
    }
    VectorStoreProperties.Milvus conf = properties.getMilvus();
    R<MutationResult> result = client.delete(DeleteParam.newBuilder()
        .withCollectionName(conf.getCollection())
        .withExpr("doc_id == " + docId)
        .build());
    check(result, "delete doc vectors");
  }

  public void deleteByKnowledgeBaseId(Long kbId) {
    if (kbId == null) {
      return;
    }
    VectorStoreProperties.Milvus conf = properties.getMilvus();
    R<MutationResult> result = client.delete(DeleteParam.newBuilder()
        .withCollectionName(conf.getCollection())
        .withExpr("kb_id == " + kbId)
        .build());
    check(result, "delete kb vectors");
  }

  public List<SearchHit> search(Long kbId, Collection<Long> allowedDocIds, List<Double> queryVector, int topK) {
    if (queryVector == null || queryVector.isEmpty()) {
      return List.of();
    }
    VectorStoreProperties.Milvus conf = properties.getMilvus();
    String expr = buildFilterExpr(kbId, allowedDocIds);
    List<List<Float>> vectors = List.of(queryVector.stream().map(Double::floatValue).toList());
    SearchParam searchParam = SearchParam.newBuilder()
        .withCollectionName(conf.getCollection())
        .withVectorFieldName("vector")
        .withMetricType(io.milvus.param.MetricType.COSINE)
        .withTopK(Math.max(1, topK))
        .withVectors(vectors)
        .withParams("{\"nprobe\":" + conf.getNprobe() + "}")
        .withExpr(expr)
        .build();
    R<io.milvus.grpc.SearchResults> resp = client.search(searchParam);
    check(resp, "search vectors");
    SearchResultsWrapper wrapper = new SearchResultsWrapper(resp.getData().getResults());
    List<SearchResultsWrapper.IDScore> scoreList = wrapper.getIDScore(0);
    List<SearchHit> hits = new ArrayList<>();
    for (SearchResultsWrapper.IDScore idScore : scoreList) {
      Long chunkId = idScore.getLongID();
      double score = idScore.getScore();
      hits.add(new SearchHit(chunkId, score));
    }
    return hits;
  }

  private String buildFilterExpr(Long kbId, Collection<Long> allowedDocIds) {
    StringBuilder expr = new StringBuilder("kb_id == ").append(kbId);
    if (allowedDocIds != null && !allowedDocIds.isEmpty()) {
      String docIds = allowedDocIds.stream().map(String::valueOf).collect(Collectors.joining(","));
      expr.append(" && doc_id in [").append(docIds).append("]");
    }
    return expr.toString();
  }

  private void ensureCollection() {
    VectorStoreProperties.Milvus conf = properties.getMilvus();
    R<Boolean> hasCollection = client.hasCollection(HasCollectionParam.newBuilder()
        .withCollectionName(conf.getCollection())
        .build());
    check(hasCollection, "has collection");
    if (Boolean.TRUE.equals(hasCollection.getData())) {
      client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(conf.getCollection()).build());
      return;
    }

    CreateCollectionParam createCollectionParam = CreateCollectionParam.newBuilder()
        .withCollectionName(conf.getCollection())
        .withDescription("knowledge chunk vectors")
        .withShardsNum(conf.getShardsNum())
        .addFieldType(FieldType.newBuilder()
            .withName("id")
            .withDataType(DataType.Int64)
            .withPrimaryKey(true)
            .withAutoID(false)
            .build())
        .addFieldType(FieldType.newBuilder()
            .withName("kb_id")
            .withDataType(DataType.Int64)
            .build())
        .addFieldType(FieldType.newBuilder()
            .withName("doc_id")
            .withDataType(DataType.Int64)
            .build())
        .addFieldType(FieldType.newBuilder()
            .withName("chunk_index")
            .withDataType(DataType.Int32)
            .build())
        .addFieldType(FieldType.newBuilder()
            .withName("vector")
            .withDataType(DataType.FloatVector)
            .withDimension(conf.getEmbeddingDim())
            .build())
        .build();
    check(client.createCollection(createCollectionParam), "create collection");

    CreateIndexParam indexParam = CreateIndexParam.newBuilder()
        .withCollectionName(conf.getCollection())
        .withFieldName("vector")
        .withIndexType(io.milvus.param.IndexType.IVF_FLAT)
        .withMetricType(io.milvus.param.MetricType.COSINE)
        .withExtraParam("{\"nlist\":1024}")
        .build();
    check(client.createIndex(indexParam), "create index");
    check(client.loadCollection(LoadCollectionParam.newBuilder()
        .withCollectionName(conf.getCollection())
        .build()), "load collection");
  }

  private void check(R<?> result, String action) {
    if (result == null || result.getStatus() != R.Status.Success.getCode()) {
      String message = result == null ? "null response" : result.getMessage();
      throw new IllegalStateException("Milvus " + action + " failed: " + message);
    }
  }

  @Data
  @AllArgsConstructor
  public static class SearchHit {
    private Long chunkId;
    private Double score;
  }

  @Data
  @AllArgsConstructor
  public static class ChunkVectorRecord {
    private Long chunkId;
    private Integer chunkIndex;
    private List<Double> vector;
  }
}
