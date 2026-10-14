package io.aigoodle.knowledge.config;

import io.aigoodle.knowledge.async.DocumentIngestionListener;
import io.aigoodle.knowledge.async.DocumentIngestionQueue;
import io.aigoodle.knowledge.async.DocumentIngestionRunner;
import io.aigoodle.knowledge.async.IngestionJobCoordinator;
import io.aigoodle.knowledge.async.IngestionRecoveryService;
import io.aigoodle.knowledge.async.KnowledgeQueueNames;
import io.aigoodle.knowledge.async.RabbitDocumentIngestionQueue;
import io.aigoodle.knowledge.async.SyncDocumentIngestionQueue;
import io.aigoodle.knowledge.chunk.Chunker;
import io.aigoodle.knowledge.chunk.ChunkerRegistry;
import io.aigoodle.knowledge.eval.RetrievalEvaluator;
import io.aigoodle.knowledge.eval.RetrievalRegressionGate;
import io.aigoodle.knowledge.index.IndexVersionService;
import io.aigoodle.knowledge.index.IndexingService;
import io.aigoodle.knowledge.index.JdbcVectorStoreFactory;
import io.aigoodle.knowledge.index.VectorStoreFactory;
import io.aigoodle.knowledge.index.VectorStoreManager;
import io.aigoodle.knowledge.mapper.DatasetMapper;
import io.aigoodle.knowledge.mapper.DocumentIngestQueueMapper;
import io.aigoodle.knowledge.mapper.IndexVersionMapper;
import io.aigoodle.knowledge.mapper.KnowledgeDocumentMapper;
import io.aigoodle.knowledge.mapper.SegmentMapper;
import io.aigoodle.knowledge.rag.DefaultRagPipeline;
import io.aigoodle.knowledge.rag.KnowledgeVectorRetriever;
import io.aigoodle.knowledge.rag.KnowledgeModelRagReranker;
import io.aigoodle.knowledge.rag.ReciprocalRankFusion;
import io.aigoodle.knowledge.reader.CsvDocumentReader;
import io.aigoodle.knowledge.reader.DocumentEnricher;
import io.aigoodle.knowledge.reader.DocumentExtractor;
import io.aigoodle.knowledge.reader.DocumentReader;
import io.aigoodle.knowledge.reader.DocumentReaderRegistry;
import io.aigoodle.knowledge.reader.DocxDocumentReader;
import io.aigoodle.knowledge.reader.HtmlDocumentReader;
import io.aigoodle.knowledge.reader.MarkdownDocumentReader;
import io.aigoodle.knowledge.reader.PdfDocumentReader;
import io.aigoodle.knowledge.reader.PresentationDocumentReader;
import io.aigoodle.knowledge.reader.SpreadsheetDocumentReader;
import io.aigoodle.knowledge.reader.TextDocumentReader;
import io.aigoodle.knowledge.reader.TikaDocumentReader;
import io.aigoodle.knowledge.rerank.ModelReranker;
import io.aigoodle.knowledge.rerank.NoopReranker;
import io.aigoodle.knowledge.rerank.Reranker;
import io.aigoodle.knowledge.rerank.RerankerRegistry;
import io.aigoodle.knowledge.rerank.WeightedReranker;
import io.aigoodle.knowledge.rerank.WeightedRerankerSettings;
import io.aigoodle.knowledge.retrieve.DefaultQueryTransformer;
import io.aigoodle.knowledge.retrieve.HybridRetriever;
import io.aigoodle.knowledge.retrieve.QueryTransformer;
import io.aigoodle.knowledge.service.DatasetService;
import io.aigoodle.knowledge.service.KnowledgeService;
import io.aigoodle.model.config.GoodleModelAutoConfiguration;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.rag.api.PlannedQuery;
import io.aigoodle.rag.api.QueryPlan;
import io.aigoodle.rag.spi.ContextProcessor;
import io.aigoodle.rag.spi.FusionStrategy;
import io.aigoodle.rag.spi.QueryPlanner;
import io.aigoodle.rag.spi.RagPipeline;
import io.aigoodle.rag.spi.RagReranker;
import io.aigoodle.rag.spi.Retriever;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Auto-configuration for the knowledge module. Builds on the model module
 * (embedding models come from {@link ModelService}) and wires the full RAG pipeline:
 * chunking, indexing, vector store management, hybrid retrieval and reranking.
 */
@AutoConfiguration(after = GoodleModelAutoConfiguration.class)
@MapperScan("io.aigoodle.knowledge.mapper")
public class GoodleKnowledgeAutoConfiguration {

    // ------------------------------------------------------------- chunkers

    @Bean
    @ConditionalOnMissingBean
    public ChunkerRegistry chunkerRegistry(ObjectProvider<Chunker> customChunkers) {
        return new ChunkerRegistry(customChunkers.orderedStream().toList());
    }

    // -------------------------------------------------------- document readers

    @Bean
    @ConditionalOnMissingBean(name = "textDocumentReader")
    public TextDocumentReader textDocumentReader() {
        return new TextDocumentReader();
    }

    @Bean
    @ConditionalOnMissingBean(name = "markdownDocumentReader")
    public MarkdownDocumentReader markdownDocumentReader() {
        return new MarkdownDocumentReader();
    }

    @Bean
    @ConditionalOnMissingBean(name = "htmlDocumentReader")
    public HtmlDocumentReader htmlDocumentReader() {
        return new HtmlDocumentReader();
    }

    @Bean
    @ConditionalOnMissingBean(name = "docxDocumentReader")
    public DocxDocumentReader docxDocumentReader() {
        return new DocxDocumentReader();
    }

    @Bean
    @ConditionalOnMissingBean(name = "pdfDocumentReader")
    public PdfDocumentReader pdfDocumentReader() {
        return new PdfDocumentReader();
    }

    @Bean
    @ConditionalOnMissingBean(name = "presentationDocumentReader")
    public PresentationDocumentReader presentationDocumentReader() {
        return new PresentationDocumentReader();
    }

    @Bean
    @ConditionalOnMissingBean(name = "spreadsheetDocumentReader")
    public SpreadsheetDocumentReader spreadsheetDocumentReader() {
        return new SpreadsheetDocumentReader();
    }

    @Bean
    @ConditionalOnMissingBean(name = "csvDocumentReader")
    public CsvDocumentReader csvDocumentReader() {
        return new CsvDocumentReader();
    }

    @Bean
    @ConditionalOnMissingBean(name = "tikaDocumentReader")
    public TikaDocumentReader tikaDocumentReader() {
        return new TikaDocumentReader();
    }

    @Bean
    @ConditionalOnMissingBean
    public DocumentReaderRegistry documentReaderRegistry(ObjectProvider<DocumentReader> readers) {
        List<DocumentReader> ordered = new ArrayList<>(readers.orderedStream().toList());
        // Ensure the Tika fallback lands last so specific readers get first crack.
        ordered.sort((a, b) -> {
            boolean aTika = TikaDocumentReader.NAME.equalsIgnoreCase(a.getName());
            boolean bTika = TikaDocumentReader.NAME.equalsIgnoreCase(b.getName());
            return Boolean.compare(aTika, bTika);
        });
        return new DocumentReaderRegistry(ordered);
    }

    @Bean
    @ConditionalOnMissingBean
    public DocumentExtractor documentExtractor(
            DocumentReaderRegistry registry, ObjectProvider<DocumentEnricher> enrichers) {
        return new DocumentExtractor(registry, enrichers.orderedStream().toList());
    }

    // ------------------------------------------------------------- rerankers

    @Bean
    @ConditionalOnMissingBean(name = "noopReranker")
    public NoopReranker noopReranker() {
        return new NoopReranker();
    }

    @Bean
    @ConditionalOnMissingBean(name = "weightedReranker")
    public WeightedReranker weightedReranker(
            @Value("${spring-agent.knowledge.reranker.weighted.vector-weight:0.6}") double vectorWeight,
            @Value("${spring-agent.knowledge.reranker.weighted.length-weight:0.1}") double lengthWeight,
            @Value("${spring-agent.knowledge.reranker.weighted.ideal-length:400}") int idealLength) {
        return new WeightedReranker(new WeightedRerankerSettings(vectorWeight, lengthWeight, idealLength));
    }

    @Bean
    @ConditionalOnMissingBean(name = "modelReranker")
    public ModelReranker modelReranker(
            ModelService modelService, @Value("${spring-agent.knowledge.reranker.model.model-id:}") String modelId) {
        return new ModelReranker(modelService, modelId);
    }

    @Bean
    @ConditionalOnMissingBean
    public RerankerRegistry rerankerRegistry(ObjectProvider<Reranker> rerankers, NoopReranker fallback) {
        return new RerankerRegistry(rerankers.orderedStream().toList(), fallback);
    }

    // ---------------------------------------------------------- vector stores

    /**
     * Persist embeddings in the relational database when
     * {@code spring-agent.knowledge.vector-store=jdbc}. Otherwise the in-memory store is used.
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring-agent.knowledge", name = "vector-store", havingValue = "jdbc")
    @ConditionalOnMissingBean(VectorStoreFactory.class)
    public VectorStoreFactory jdbcVectorStoreFactory(
            JdbcTemplate jdbcTemplate,
            @Value("${spring-agent.knowledge.vector-table:goodle_embeddings}") String table) {
        return new JdbcVectorStoreFactory(jdbcTemplate, table);
    }

    @Bean
    @ConditionalOnMissingBean
    public VectorStoreManager vectorStoreManager(
            ModelService modelService, ObjectProvider<VectorStoreFactory> factory) {
        return new VectorStoreManager(modelService, factory.getIfAvailable());
    }

    // ------------------------------------------------------------- pipeline

    @Bean
    @ConditionalOnMissingBean(name = "defaultQueryTransformer")
    public DefaultQueryTransformer defaultQueryTransformer() {
        return new DefaultQueryTransformer();
    }

    @Bean
    @ConditionalOnMissingBean
    public IndexingService indexingService(SegmentMapper segmentMapper, VectorStoreManager vectorStoreManager) {
        return new IndexingService(segmentMapper, vectorStoreManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public IndexVersionService indexVersionService(IndexVersionMapper indexVersionMapper, DatasetMapper datasetMapper) {
        return new IndexVersionService(indexVersionMapper, datasetMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public HybridRetriever hybridRetriever(
            SegmentMapper segmentMapper,
            VectorStoreManager vectorStoreManager,
            RerankerRegistry rerankerRegistry,
            ObjectProvider<QueryTransformer> queryTransformers) {
        return new HybridRetriever(
                segmentMapper,
                vectorStoreManager,
                rerankerRegistry,
                queryTransformers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean(QueryPlanner.class)
    public QueryPlanner directRagQueryPlanner() {
        return new QueryPlanner() {
            @Override
            public String getName() {
                return "DIRECT";
            }

            @Override
            public QueryPlan plan(io.aigoodle.rag.api.RagRequest request) {
                return new QueryPlan(
                        request.query(),
                        List.of(new PlannedQuery("q0", request.query(), PlannedQuery.Type.ORIGINAL, 1.0, null)));
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(KnowledgeVectorRetriever.class)
    public KnowledgeVectorRetriever knowledgeVectorRetriever(
            DatasetService datasetService, HybridRetriever hybridRetriever) {
        return new KnowledgeVectorRetriever(datasetService, hybridRetriever);
    }

    @Bean
    @ConditionalOnMissingBean(KnowledgeModelRagReranker.class)
    public KnowledgeModelRagReranker knowledgeModelRagReranker(ModelService modelService) {
        return new KnowledgeModelRagReranker(modelService);
    }

    @Bean
    @ConditionalOnMissingBean(ReciprocalRankFusion.class)
    public ReciprocalRankFusion reciprocalRankFusion() {
        return new ReciprocalRankFusion();
    }

    @Bean
    @ConditionalOnMissingBean(RagPipeline.class)
    public RagPipeline ragPipeline(
            ObjectProvider<QueryPlanner> planners,
            ObjectProvider<Retriever> retrievers,
            ObjectProvider<FusionStrategy> fusionStrategies,
            ObjectProvider<RagReranker> rerankers,
            ObjectProvider<ContextProcessor> contextProcessors) {
        return new DefaultRagPipeline(
                planners.orderedStream().toList(),
                retrievers.orderedStream().toList(),
                fusionStrategies.orderedStream().toList(),
                rerankers.orderedStream().toList(),
                contextProcessors.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public RetrievalEvaluator retrievalEvaluator() {
        return new RetrievalEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean
    public RetrievalRegressionGate retrievalRegressionGate() {
        return new RetrievalRegressionGate();
    }

    @Bean
    @ConditionalOnMissingBean
    public DatasetService datasetService(DatasetMapper datasetMapper) {
        return new DatasetService(datasetMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public KnowledgeService knowledgeService(
            DatasetService datasetService,
            KnowledgeDocumentMapper documentMapper,
            ChunkerRegistry chunkerRegistry,
            IndexingService indexingService,
            HybridRetriever retriever,
            DocumentExtractor extractor,
            io.aigoodle.knowledge.mapper.HitTestingLogMapper hitTestingLogMapper,
            ObjectProvider<DocumentIngestionQueue> ingestionQueue,
            ObjectProvider<DocumentIngestQueueMapper> queueMapper) {
        KnowledgeService service = new KnowledgeService(
                datasetService, documentMapper, chunkerRegistry, indexingService, retriever, extractor);
        service.setHitTestingLogMapper(hitTestingLogMapper);
        // Wire async only when both the queue and its sidecar mapper are on
        // the classpath (they come as a pair from the async auto-config below).
        DocumentIngestionQueue q = ingestionQueue.getIfAvailable();
        DocumentIngestQueueMapper m = queueMapper.getIfAvailable();
        if (q != null && m != null) {
            service.setIngestionQueue(q, m);
        }
        return service;
    }

    // ============================================================
    //  Async ingestion pipeline (opt-in via
    //  spring-agent.knowledge.async.enabled=true).
    //
    //  Two modes:
    //   * "sync" (default when async is on but no broker present)
    //       — SyncDocumentIngestionQueue: background thread pool inside the
    //         JVM. Still gets the upload-returns-immediately win, without
    //         requiring an external broker.
    //   * "rabbit" — RabbitDocumentIngestionQueue + @RabbitListener worker.
    //         Durable at-least-once semantics + separate worker scaling.
    // ============================================================

    /**
     * Runner is the pure "extract → clean → chunk → index" pipeline; both
     * queue implementations delegate to it. Always registered when async is
     * on; harmless idle bean when it's off.
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring-agent.knowledge.async", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public IngestionJobCoordinator ingestionJobCoordinator(
            DocumentIngestQueueMapper queueMapper,
            @Value("${spring-agent.knowledge.async.lease-duration:PT5M}") Duration leaseDuration,
            @Value("${spring-agent.knowledge.async.retry-base-delay:PT2S}") Duration baseDelay,
            @Value("${spring-agent.knowledge.async.max-attempts:5}") int maxAttempts) {
        return new IngestionJobCoordinator(queueMapper, leaseDuration, baseDelay, maxAttempts);
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring-agent.knowledge.async", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public DocumentIngestionRunner documentIngestionRunner(
            DatasetService datasetService,
            KnowledgeDocumentMapper documentMapper,
            DocumentIngestQueueMapper queueMapper,
            ChunkerRegistry chunkerRegistry,
            IndexingService indexingService,
            IngestionJobCoordinator coordinator) {
        return new DocumentIngestionRunner(
                datasetService, documentMapper, queueMapper, chunkerRegistry, indexingService, coordinator);
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring-agent.knowledge.async", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public IngestionRecoveryService ingestionRecoveryService(
            IngestionJobCoordinator coordinator, DocumentIngestionQueue queue) {
        return new IngestionRecoveryService(coordinator, queue);
    }

    /**
     * In-memory queue — active when async is on but the host hasn't
     * configured RabbitMQ (no {@code spring.rabbitmq.host} → no
     * {@code RabbitTemplate} bean → we fall back to this). Thread count via
     * {@code spring-agent.knowledge.async.worker-threads}, default 4.
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring-agent.knowledge.async", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "documentIngestionQueue")
    @org.springframework.context.annotation.Conditional(NoRabbitTemplateCondition.class)
    public DocumentIngestionQueue syncDocumentIngestionQueue(
            DocumentIngestionRunner runner,
            @Value("${spring-agent.knowledge.async.worker-threads:4}") int workerThreads) {
        return new SyncDocumentIngestionQueue(runner, workerThreads);
    }

    /**
     * RabbitMQ-backed queue + topology + listener — active when async is on
     * AND the host has a {@code RabbitTemplate} bean (starter-amqp auto-config
     * picked up {@code spring.rabbitmq.host}).
     */
    @org.springframework.context.annotation.Configuration
    @ConditionalOnProperty(prefix = "spring-agent.knowledge.async", name = "enabled", havingValue = "true")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(
            name = "org.springframework.amqp.rabbit.core.RabbitTemplate")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(
            org.springframework.amqp.rabbit.core.RabbitTemplate.class)
    public static class RabbitAsyncConfig {

        @Bean(name = "documentIngestionQueue")
        @ConditionalOnMissingBean(name = "documentIngestionQueue")
        public DocumentIngestionQueue documentIngestionQueue(
                org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate) {
            return new RabbitDocumentIngestionQueue(rabbitTemplate);
        }

        @Bean
        @ConditionalOnMissingBean
        public DocumentIngestionListener documentIngestionListener(DocumentIngestionRunner runner) {
            return new DocumentIngestionListener(runner);
        }

        /**
         * JSON converter so {@link io.aigoodle.knowledge.async.DocumentIngestionTask}
         * ships as plain JSON instead of Java serialisation (portable + safer).
         */
        @Bean
        @ConditionalOnMissingBean(org.springframework.amqp.support.converter.Jackson2JsonMessageConverter.class)
        public org.springframework.amqp.support.converter.Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
            return new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter();
        }

        /**
         * Attach the JSON converter to the RabbitTemplate and to the listener
         * container factory so publish and consume both use it.
         */
        @Bean
        public org.springframework.beans.factory.config.BeanPostProcessor knowledgeRabbitTemplateConverterPostProcessor(
                org.springframework.amqp.support.converter.Jackson2JsonMessageConverter converter) {
            return new org.springframework.beans.factory.config.BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof org.springframework.amqp.rabbit.core.RabbitTemplate rt) {
                        rt.setMessageConverter(converter);
                    }
                    if (bean instanceof org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory f) {
                        f.setMessageConverter(converter);
                    }
                    return bean;
                }
            };
        }

        // Topology — main exchange/queue with DLX pointing at the DLQ.
        // Messages that Spring rejects with AmqpRejectAndDontRequeueException
        // land on the DLQ automatically.

        @Bean
        public org.springframework.amqp.core.DirectExchange kbDocumentExchange() {
            return new org.springframework.amqp.core.DirectExchange(KnowledgeQueueNames.EXCHANGE, true, false);
        }

        @Bean
        public org.springframework.amqp.core.DirectExchange kbDocumentDlqExchange() {
            return new org.springframework.amqp.core.DirectExchange(KnowledgeQueueNames.DLQ_EXCHANGE, true, false);
        }

        @Bean
        public org.springframework.amqp.core.Queue kbDocumentIngestQueue() {
            return org.springframework.amqp.core.QueueBuilder.durable(KnowledgeQueueNames.QUEUE)
                    .withArgument("x-dead-letter-exchange", KnowledgeQueueNames.DLQ_EXCHANGE)
                    .withArgument("x-dead-letter-routing-key", KnowledgeQueueNames.DLQ_ROUTING_KEY)
                    .build();
        }

        @Bean
        public org.springframework.amqp.core.Queue kbDocumentIngestDlqQueue() {
            return org.springframework.amqp.core.QueueBuilder.durable(KnowledgeQueueNames.DLQ_QUEUE)
                    .build();
        }

        @Bean
        public org.springframework.amqp.core.Binding kbDocumentIngestBinding(
                org.springframework.amqp.core.Queue kbDocumentIngestQueue,
                org.springframework.amqp.core.DirectExchange kbDocumentExchange) {
            return org.springframework.amqp.core.BindingBuilder.bind(kbDocumentIngestQueue)
                    .to(kbDocumentExchange)
                    .with(KnowledgeQueueNames.ROUTING_KEY);
        }

        @Bean
        public org.springframework.amqp.core.Binding kbDocumentIngestDlqBinding(
                org.springframework.amqp.core.Queue kbDocumentIngestDlqQueue,
                org.springframework.amqp.core.DirectExchange kbDocumentDlqExchange) {
            return org.springframework.amqp.core.BindingBuilder.bind(kbDocumentIngestDlqQueue)
                    .to(kbDocumentDlqExchange)
                    .with(KnowledgeQueueNames.DLQ_ROUTING_KEY);
        }
    }

    /**
     * Selector: registers the sync queue only when no RabbitTemplate is on
     * the classpath (or no bean present). Kept as a static inner class so
     * ConditionalOnMissingBean is deferred until Spring context refresh.
     */
    static class NoRabbitTemplateCondition
            extends org.springframework.boot.autoconfigure.condition.NoneNestedConditions {
        NoRabbitTemplateCondition() {
            super(org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
        }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(
                org.springframework.amqp.rabbit.core.RabbitTemplate.class)
        static class HasRabbit {}
    }
}
