/**
 * i18n message catalog for @agent-start/knowledge-hub.
 *
 * Every user-visible string is keyed here. Extending: a host can pass a
 * partial catalog to `<KnowledgeHubApp :locale="myLocale">` to override any
 * key without shipping a whole new catalog. Missing keys fall back to en-US
 * (which is complete).
 */

export interface KhMessages {
  app: {
    title: string;
    description: string;
    searchPlaceholder: string;
    loading: string;
    /**
     * First-time-use nudge shown at the top of the list when the tenant has
     * no embedding model registered yet. Falls back to Chinese defaults; a
     * host can override the whole catalog via {@code <KnowledgeHubApp :locale>}.
     */
    nudgeEmbeddingTitle: string;
    nudgeEmbeddingDesc: string;
    nudgeEmbeddingCta: string;
  };
  card: {
    createTitle: string;
    createSubtitle: string;
    noDescription: string;
    docs: string;
    chunks: string;
    delete: string;
    confirmDelete: string;
    more: string;
    open: string;
    settings: string;
  };
  wizard: {
    stepChooseSource: string;
    stepChunking: string;
    stepDone: string;
    back: string;
    next: string;
    prev: string;
    saveAndProcess: string;
    createEmpty: string;
    createEmptyPromptTitle: string;
    createEmptyPromptDefault: string;
    chooseSource: string;
    uploadFiles: string;
    dropOrChoose: string;
    dropZoneHint: string;
  };
  detail: {
    tabDocuments: string;
    tabPipeline: string;
    tabRecall: string;
    tabSettings: string;
    accessApi: string;
    apiCopied: string;
    apiCopyFailed: string;
    pipelineStubTitle: string;
    pipelineStubSub: string;
  };
  documents: {
    intro: string;
    addFile: string;
    metadata: string;
    filterAll: string;
    filterAvailable: string;
    filterFailed: string;
    searchPlaceholder: string;
    sortUploadedDesc: string;
    sortUploadedAsc: string;
    sortName: string;
    colHash: string;
    colName: string;
    colMode: string;
    colWords: string;
    colHits: string;
    colUploadedAt: string;
    colStatus: string;
    colActions: string;
    statusAvailable: string;
    statusDisabled: string;
    statusFailed: string;
    statusProcessing: string;
    emptyNoDocuments: string;
    emptyNoMatches: string;
  };
  chunks: {
    addChunk: string;
    filterAll: string;
    filterEnabled: string;
    filterDisabled: string;
    emptyNoMatches: string;
    enabled: string;
    disabled: string;
    editorTitle: string;
    editorHint: string;
    save: string;
    cancel: string;
    delete: string;
    confirmDeleteChunk: string;
  };
  recall: {
    title: string;
    subtitle: string;
    sourceText: string;
    placeholder: string;
    test: string;
    methodVector: string;
    methodFullText: string;
    methodHybrid: string;
    recentTitle: string;
    recentEmpty: string;
    resultsEmpty: string;
    hits: string;
  };
  settings: {
    title: string;
    subtitle: string;
    labelName: string;
    labelDescription: string;
    labelPermission: string;
    labelChunkMode: string;
    labelIndexing: string;
    labelEmbedding: string;
    labelAutoSummary: string;
    labelRetrieval: string;
    permissionOnlyMe: string;
    permissionOrg: string;
    save: string;
    cancel: string;
    saved: string;
    saveFailed: string;
  };
  common: {
    ok: string;
    cancel: string;
    delete: string;
    confirm: string;
    createdSuccess: string;
    createFailed: string;
    uploadFailed: string;
    loadFailed: string;
    deleteSuccess: string;
  };
}

export const zhCN: KhMessages = {
  app: {
    title: '知识库',
    description: 'Datasets · 文档 · 检索。',
    searchPlaceholder: '搜索知识库...',
    loading: '加载中…',
    nudgeEmbeddingTitle: '第一步：先注册一个 Embedding 模型',
    nudgeEmbeddingDesc:
      '高质量知识库需要 Embedding 模型来向量化文档。也可以直接创建"经济"模式的知识库，只用关键词检索。',
    nudgeEmbeddingCta: '去配置模型',
  },
  card: {
    createTitle: '新建知识库',
    createSubtitle: '导入文档 / 上传文件 / 空知识库',
    noDescription: '暂无描述',
    docs: '文档',
    chunks: '片段',
    delete: '删除',
    confirmDelete: '确认删除「{name}」？该操作不可恢复。',
    more: '更多操作',
    open: '打开',
    settings: '设置',
  },
  wizard: {
    stepChooseSource: '选择数据源',
    stepChunking: '文本分段与清洗',
    stepDone: '处理并完成',
    back: '返回',
    next: '下一步',
    prev: '上一步',
    saveAndProcess: '保存并处理',
    createEmpty: '创建一个空知识库',
    createEmptyPromptTitle: '新知识库名称',
    createEmptyPromptDefault: '空知识库',
    chooseSource: '选择数据源',
    uploadFiles: '上传文本文件',
    dropOrChoose: '拖拽文件或文件夹至此，或者',
    dropZoneHint: '每批最多 5 个文件，每个文件不超过 15 MB。',
  },
  detail: {
    tabDocuments: '文档',
    tabPipeline: '流水线',
    tabRecall: '召回测试',
    tabSettings: '设置',
    accessApi: '访问 API',
    apiCopied: '已复制 API 地址',
    apiCopyFailed: '复制失败',
    pipelineStubTitle: '流水线',
    pipelineStubSub: '未来将在此展示数据处理流水线（清洗 · 分段 · 嵌入 · 索引）的实时状态。',
  },
  documents: {
    intro: '知识库的所有文件都在这里显示。',
    addFile: '添加文件',
    metadata: '元数据',
    filterAll: '全部',
    filterAvailable: '可用',
    filterFailed: '失败',
    searchPlaceholder: '搜索',
    sortUploadedDesc: '排序：上传时间 ↓',
    sortUploadedAsc: '排序：上传时间 ↑',
    sortName: '排序：名称',
    colHash: '#',
    colName: '名称',
    colMode: '分段模式',
    colWords: '字数',
    colHits: '召回次数',
    colUploadedAt: '上传时间',
    colStatus: '状态',
    colActions: '操作',
    statusAvailable: '可用',
    statusDisabled: '已停用',
    statusFailed: '失败',
    statusProcessing: '处理中',
    emptyNoDocuments: '还没有文档。点击右上角"添加文件"上传。',
    emptyNoMatches: '没有匹配的文档',
  },
  chunks: {
    addChunk: '添加分段',
    filterAll: '全部',
    filterEnabled: '已启用',
    filterDisabled: '已停用',
    emptyNoMatches: '没有匹配的分段',
    enabled: '已启用',
    disabled: '已停用',
    editorTitle: '编辑分段',
    editorHint: '保存后会重新计算 embedding 并入向量库，可能需要几秒。',
    save: '保存',
    cancel: '取消',
    delete: '删除',
    confirmDeleteChunk: '删除后该分段不会再被检索，操作不可撤销。',
  },
  recall: {
    title: '召回测试',
    subtitle: '根据给定的查询文本测试知识的召回效果。',
    sourceText: '源文本',
    placeholder: '请输入文本，建议使用简短的陈述句。',
    test: '测试',
    methodVector: '向量检索',
    methodFullText: '全文检索',
    methodHybrid: '混合检索',
    recentTitle: '记录',
    recentEmpty: '最近无查询结果',
    resultsEmpty: '召回测试结果显示在这里',
    hits: '命中 {n} 个片段',
  },
  settings: {
    title: '知识库设置',
    subtitle: '在这里，您可以修改此知识库的属性和检索设置',
    labelName: '名称和图标',
    labelDescription: '描述',
    labelPermission: '可见权限',
    labelChunkMode: '分段模式',
    labelIndexing: '索引模式',
    labelEmbedding: 'Embedding 模型',
    labelAutoSummary: '摘要自动生成',
    labelRetrieval: '检索设置',
    permissionOnlyMe: '只有我',
    permissionOrg: '团队成员',
    save: '保存',
    cancel: '取消',
    saved: '设置已保存',
    saveFailed: '保存失败',
  },
  common: {
    ok: '确定',
    cancel: '取消',
    delete: '删除',
    confirm: '确认',
    createdSuccess: '已创建',
    createFailed: '创建失败',
    uploadFailed: '上传失败',
    loadFailed: '加载失败',
    deleteSuccess: '已删除',
  },
};

export const enUS: KhMessages = {
  app: {
    title: 'Knowledge Base',
    description: 'Datasets · Documents · Retrieval.',
    searchPlaceholder: 'Search knowledge bases...',
    loading: 'Loading…',
    nudgeEmbeddingTitle: 'Step 1 — Register an embedding model first',
    nudgeEmbeddingDesc:
      'High-quality knowledge bases need an embedding model to vectorise documents. You can also create an "economy" KB that only uses keyword search.',
    nudgeEmbeddingCta: 'Configure a model',
  },
  card: {
    createTitle: 'New Knowledge Base',
    createSubtitle: 'Import documents / upload files / empty KB',
    noDescription: 'No description',
    docs: 'Docs',
    chunks: 'Chunks',
    delete: 'Delete',
    confirmDelete: 'Delete "{name}"? This cannot be undone.',
    more: 'More',
    open: 'Open',
    settings: 'Settings',
  },
  wizard: {
    stepChooseSource: 'Choose Data Source',
    stepChunking: 'Chunking & Cleaning',
    stepDone: 'Process & Done',
    back: 'Back',
    next: 'Next',
    prev: 'Previous',
    saveAndProcess: 'Save & Process',
    createEmpty: 'Create empty knowledge base',
    createEmptyPromptTitle: 'New knowledge base name',
    createEmptyPromptDefault: 'Empty KB',
    chooseSource: 'Choose data source',
    uploadFiles: 'Upload text files',
    dropOrChoose: 'Drag files or folders here, or',
    dropZoneHint: 'Up to 5 files per batch, 15 MB each.',
  },
  detail: {
    tabDocuments: 'Documents',
    tabPipeline: 'Pipeline',
    tabRecall: 'Recall Testing',
    tabSettings: 'Settings',
    accessApi: 'Access API',
    apiCopied: 'API URL copied',
    apiCopyFailed: 'Copy failed',
    pipelineStubTitle: 'Pipeline',
    pipelineStubSub: 'Live status of clean · chunk · embed · index steps will appear here.',
  },
  documents: {
    intro: 'All files in the knowledge base are shown here.',
    addFile: 'Add File',
    metadata: 'Metadata',
    filterAll: 'All',
    filterAvailable: 'Available',
    filterFailed: 'Failed',
    searchPlaceholder: 'Search',
    sortUploadedDesc: 'Sort: uploaded ↓',
    sortUploadedAsc: 'Sort: uploaded ↑',
    sortName: 'Sort: name',
    colHash: '#',
    colName: 'Name',
    colMode: 'Chunk mode',
    colWords: 'Words',
    colHits: 'Recalls',
    colUploadedAt: 'Uploaded at',
    colStatus: 'Status',
    colActions: 'Actions',
    statusAvailable: 'Available',
    statusDisabled: 'Disabled',
    statusFailed: 'Failed',
    statusProcessing: 'Processing',
    emptyNoDocuments: 'No documents yet. Click "Add File" to upload.',
    emptyNoMatches: 'No matching documents',
  },
  chunks: {
    addChunk: 'Add chunk',
    filterAll: 'All',
    filterEnabled: 'Enabled',
    filterDisabled: 'Disabled',
    emptyNoMatches: 'No matching chunks',
    enabled: 'Enabled',
    disabled: 'Disabled',
    editorTitle: 'Edit chunk',
    editorHint: 'Saving re-embeds this chunk. Takes a few seconds.',
    save: 'Save',
    cancel: 'Cancel',
    delete: 'Delete',
    confirmDeleteChunk: 'This chunk will no longer match retrieval queries.',
  },
  recall: {
    title: 'Recall Testing',
    subtitle: 'Test how well this knowledge base recalls for a given query.',
    sourceText: 'Source text',
    placeholder: 'Enter text — a short declarative sentence works best.',
    test: 'Test',
    methodVector: 'Vector',
    methodFullText: 'Full-text',
    methodHybrid: 'Hybrid',
    recentTitle: 'History',
    recentEmpty: 'No recent queries',
    resultsEmpty: 'Recall results will appear here',
    hits: 'Matched {n} chunks',
  },
  settings: {
    title: 'Knowledge Base Settings',
    subtitle: 'Adjust the identity and retrieval config of this knowledge base.',
    labelName: 'Name & Icon',
    labelDescription: 'Description',
    labelPermission: 'Visibility',
    labelChunkMode: 'Chunk mode',
    labelIndexing: 'Index mode',
    labelEmbedding: 'Embedding model',
    labelAutoSummary: 'Auto summary',
    labelRetrieval: 'Retrieval',
    permissionOnlyMe: 'Only me',
    permissionOrg: 'Team members',
    save: 'Save',
    cancel: 'Cancel',
    saved: 'Settings saved',
    saveFailed: 'Save failed',
  },
  common: {
    ok: 'OK',
    cancel: 'Cancel',
    delete: 'Delete',
    confirm: 'Confirm',
    createdSuccess: 'Created',
    createFailed: 'Create failed',
    uploadFailed: 'Upload failed',
    loadFailed: 'Load failed',
    deleteSuccess: 'Deleted',
  },
};

export const DEFAULT_LOCALE: KhMessages = zhCN;
