<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue';

defineOptions({
  name: 'KnowledgeGraph'
});

interface GraphNode {
  id: string;
  label: string;
  type: 'document' | 'entity' | 'concept' | 'person' | 'organization';
  size: number;
  x?: number;
  y?: number;
  vx?: number;
  vy?: number;
  fx?: number | null;
  fy?: number | null;
}

interface GraphEdge {
  id: string;
  source: string;
  target: string;
  label?: string;
}

interface GraphData {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

const canvasRef = ref<HTMLCanvasElement>();
const containerRef = ref<HTMLDivElement>();
const loading = ref(false);
const selectedNode = ref<GraphNode | null>(null);
const hoveredNode = ref<GraphNode | null>(null);
const searchKeyword = ref('');
const showLabels = ref(true);

// 模拟图数据
const graphData = ref<GraphData>({
  nodes: [
    { id: '1', label: '产品需求文档', type: 'document', size: 30 },
    { id: '2', label: '技术架构方案', type: 'document', size: 28 },
    { id: '3', label: '用户研究报告', type: 'document', size: 26 },
    { id: '4', label: 'API接口设计', type: 'document', size: 24 },
    { id: '5', label: '张三', type: 'person', size: 20 },
    { id: '6', label: '李四', type: 'person', size: 20 },
    { id: '7', label: '产品部', type: 'organization', size: 22 },
    { id: '8', label: '技术部', type: 'organization', size: 22 },
    { id: '9', label: '用户认证', type: 'concept', size: 18 },
    { id: '10', label: '数据存储', type: 'concept', size: 18 },
    { id: '11', label: 'API网关', type: 'concept', size: 18 },
    { id: '12', label: '微服务架构', type: 'concept', size: 16 },
    { id: '13', label: '安全审计', type: 'concept', size: 16 },
    { id: '14', label: '性能优化', type: 'concept', size: 16 },
  ],
  edges: [
    { id: 'e1', source: '1', target: '5', label: '创建者' },
    { id: 'e2', source: '1', target: '7', label: '所属部门' },
    { id: 'e3', source: '2', target: '6', label: '创建者' },
    { id: 'e4', source: '2', target: '8', label: '所属部门' },
    { id: 'e5', source: '3', target: '7', label: '所属部门' },
    { id: 'e6', source: '4', target: '8', label: '所属部门' },
    { id: 'e7', source: '1', target: '9', label: '涉及' },
    { id: 'e8', source: '2', target: '10', label: '涉及' },
    { id: 'e9', source: '2', target: '11', label: '涉及' },
    { id: 'e10', source: '2', target: '12', label: '涉及' },
    { id: 'e11', source: '4', target: '11', label: '涉及' },
    { id: 'e12', source: '4', target: '9', label: '涉及' },
    { id: 'e13', source: '3', target: '9', label: '涉及' },
    { id: 'e14', source: '1', target: '2', label: '关联' },
    { id: 'e15', source: '2', target: '4', label: '关联' },
    { id: 'e16', source: '9', target: '13', label: '相关' },
    { id: 'e17', source: '10', target: '14', label: '相关' },
    { id: 'e18', source: '12', target: '14', label: '相关' },
  ]
});

// 节点颜色
const nodeColors: Record<string, string> = {
  document: '#667eea',
  entity: '#f093fb',
  concept: '#4fd1c5',
  person: '#f6ad55',
  organization: '#fc8181'
};

// Canvas 绘制参数
let animationId: number;
let width = 800;
let height = 600;
const nodeMap = new Map<string, GraphNode>();
const edgeMap = new Map<string, GraphEdge>();

// 力导向模拟参数
const repulsion = 5000;
const attraction = 0.01;
const damping = 0.9;
const centerForce = 0.01;

// 初始化节点位置
function initPositions() {
  graphData.value.nodes.forEach((node, i) => {
    const angle = (2 * Math.PI * i) / graphData.value.nodes.length;
    const radius = Math.min(width, height) * 0.3;
    node.x = width / 2 + radius * Math.cos(angle);
    node.y = height / 2 + radius * Math.sin(angle);
    node.vx = 0;
    node.vy = 0;
    nodeMap.set(node.id, node);
  });

  graphData.value.edges.forEach(edge => {
    edgeMap.set(edge.id, edge);
  });
}

// 力导向模拟
function simulate() {
  const nodes = graphData.value.nodes;
  const edges = graphData.value.edges;

  // 斥力
  for (let i = 0; i < nodes.length; i++) {
    for (let j = i + 1; j < nodes.length; j++) {
      const dx = nodes[j].x! - nodes[i].x!;
      const dy = nodes[j].y! - nodes[i].y!;
      const dist = Math.sqrt(dx * dx + dy * dy) || 1;
      const force = repulsion / (dist * dist);
      const fx = (dx / dist) * force;
      const fy = (dy / dist) * force;

      nodes[i].vx! -= fx;
      nodes[i].vy! -= fy;
      nodes[j].vx! += fx;
      nodes[j].vy! += fy;
    }
  }

  // 引力（边连接的节点）
  edges.forEach(edge => {
    const source = nodeMap.get(edge.source);
    const target = nodeMap.get(edge.target);
    if (!source || !target) return;

    const dx = target.x! - source.x!;
    const dy = target.y! - source.y!;
    const dist = Math.sqrt(dx * dx + dy * dy) || 1;
    const force = dist * attraction;
    const fx = (dx / dist) * force;
    const fy = (dy / dist) * force;

    source.vx! += fx;
    source.vy! += fy;
    target.vx! -= fx;
    target.vy! -= fy;
  });

  // 中心引力
  nodes.forEach(node => {
    const dx = width / 2 - node.x!;
    const dy = height / 2 - node.y!;
    node.vx! += dx * centerForce;
    node.vy! += dy * centerForce;
  });

  // 更新位置
  nodes.forEach(node => {
    if (node.fx !== null && node.fy !== null) return;

    node.vx! *= damping;
    node.vy! *= damping;
    node.x! += node.vx!;
    node.y! += node.vy!;

    // 边界约束
    node.x = Math.max(node.size, Math.min(width - node.size, node.x!));
    node.y = Math.max(node.size, Math.min(height - node.size, node.y!));
  });
}

// 绘制
function draw() {
  const canvas = canvasRef.value;
  if (!canvas) return;

  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  ctx.clearRect(0, 0, width, height);

  // 绘制边
  graphData.value.edges.forEach(edge => {
    const source = nodeMap.get(edge.source);
    const target = nodeMap.get(edge.target);
    if (!source || !target) return;

    ctx.beginPath();
    ctx.moveTo(source.x!, source.y!);
    ctx.lineTo(target.x!, target.y!);
    ctx.strokeStyle = 'rgba(150, 150, 150, 0.3)';
    ctx.lineWidth = 1;
    ctx.stroke();

    // 绘制边标签
    if (showLabels.value && edge.label) {
      const midX = (source.x! + target.x!) / 2;
      const midY = (source.y! + target.y!) / 2;
      ctx.font = '10px sans-serif';
      ctx.fillStyle = 'rgba(100, 100, 100, 0.6)';
      ctx.textAlign = 'center';
      ctx.fillText(edge.label, midX, midY);
    }
  });

  // 绘制节点
  graphData.value.nodes.forEach(node => {
    const isHovered = hoveredNode.value?.id === node.id;
    const isSelected = selectedNode.value?.id === node.id;

    // 节点圆形
    ctx.beginPath();
    ctx.arc(node.x!, node.y!, node.size * (isHovered ? 1.2 : 1), 0, 2 * Math.PI);
    ctx.fillStyle = nodeColors[node.type] || '#667eea';
    ctx.globalAlpha = isSelected ? 1 : (isHovered ? 0.9 : 0.7);
    ctx.fill();
    ctx.globalAlpha = 1;

    // 边框
    if (isSelected || isHovered) {
      ctx.strokeStyle = '#fff';
      ctx.lineWidth = 2;
      ctx.stroke();
    }

    // 节点标签
    if (showLabels.value) {
      ctx.font = `${isHovered ? 'bold ' : ''}12px sans-serif`;
      ctx.fillStyle = '#333';
      ctx.textAlign = 'center';
      ctx.fillText(node.label, node.x!, node.y! + node.size + 16);
    }
  });
}

// 动画循环
function animate() {
  simulate();
  draw();
  animationId = requestAnimationFrame(animate);
}

// 鼠标交互
function handleMouseMove(e: MouseEvent) {
  const canvas = canvasRef.value;
  if (!canvas) return;

  const rect = canvas.getBoundingClientRect();
  const x = e.clientX - rect.left;
  const y = e.clientY - rect.top;

  // 检测悬停节点
  let found: GraphNode | null = null;
  for (const node of graphData.value.nodes) {
    const dx = x - node.x!;
    const dy = y - node.y!;
    if (dx * dx + dy * dy < node.size * node.size) {
      found = node;
      break;
    }
  }

  hoveredNode.value = found;
  canvas.style.cursor = found ? 'pointer' : 'default';
}

function handleClick(e: MouseEvent) {
  const canvas = canvasRef.value;
  if (!canvas) return;

  const rect = canvas.getBoundingClientRect();
  const x = e.clientX - rect.left;
  const y = e.clientY - rect.top;

  for (const node of graphData.value.nodes) {
    const dx = x - node.x!;
    const dy = y - node.y!;
    if (dx * dx + dy * dy < node.size * node.size) {
      selectedNode.value = node;
      return;
    }
  }

  selectedNode.value = null;
}

// 搜索节点
function handleSearch() {
  const keyword = searchKeyword.value.trim().toLowerCase();
  if (!keyword) {
    selectedNode.value = null;
    return;
  }

  const found = graphData.value.nodes.find(n =>
    n.label.toLowerCase().includes(keyword)
  );
  if (found) {
    selectedNode.value = found;
  }
}

// 重置视图
function handleReset() {
  selectedNode.value = null;
  searchKeyword.value = '';
  initPositions();
}

// 获取节点关联信息
const relatedNodes = computed(() => {
  if (!selectedNode.value) return [];

  const related: { node: GraphNode; relation: string }[] = [];
  graphData.value.edges.forEach(edge => {
    if (edge.source === selectedNode.value!.id) {
      const target = nodeMap.get(edge.target);
      if (target) related.push({ node: target, relation: edge.label || '关联' });
    } else if (edge.target === selectedNode.value!.id) {
      const source = nodeMap.get(edge.source);
      if (source) related.push({ node: source, relation: edge.label || '关联' });
    }
  });
  return related;
});

// 筛选节点
const filteredNodes = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase();
  if (!keyword) return graphData.value.nodes;
  return graphData.value.nodes.filter(n =>
    n.label.toLowerCase().includes(keyword)
  );
});

// 调整画布大小
function resizeCanvas() {
  const container = containerRef.value;
  const canvas = canvasRef.value;
  if (!container || !canvas) return;

  width = container.clientWidth;
  height = container.clientHeight;
  canvas.width = width;
  canvas.height = height;
}

// 生命周期
onMounted(() => {
  resizeCanvas();
  initPositions();
  animate();

  window.addEventListener('resize', resizeCanvas);
});

onUnmounted(() => {
  cancelAnimationFrame(animationId);
  window.removeEventListener('resize', resizeCanvas);
});

watch(searchKeyword, handleSearch);
</script>

<template>
  <div class="knowledge-graph-page">
    <!-- 工具栏 -->
    <div class="toolbar">
      <NInput
        v-model:value="searchKeyword"
        placeholder="搜索节点..."
        clearable
        style="width: 200px"
      >
        <template #prefix>
          <icon-ant-design:search-outlined />
        </template>
      </NInput>

      <NButton quaternary @click="showLabels = !showLabels">
        <template #icon>
          <icon-ant-design:tag-outlined />
        </template>
        {{ showLabels ? '隐藏标签' : '显示标签' }}
      </NButton>

      <NButton quaternary @click="handleReset">
        <template #icon>
          <icon-ant-design:reload-outlined />
        </template>
        重置
      </NButton>

      <div class="legend">
        <span class="legend-item"><span class="dot document" />文档</span>
        <span class="legend-item"><span class="dot person" />人物</span>
        <span class="legend-item"><span class="dot organization" />组织</span>
        <span class="legend-item"><span class="dot concept" />概念</span>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- 图谱画布 -->
      <div ref="containerRef" class="graph-container">
        <canvas ref="canvasRef" @mousemove="handleMouseMove" @click="handleClick" />
      </div>

      <!-- 信息面板 -->
      <div v-if="selectedNode" class="info-panel">
        <div class="panel-header">
          <span class="node-type" :class="selectedNode.type">{{ selectedNode.type }}</span>
          <NButton quaternary size="small" @click="selectedNode = null">
            <icon-ant-design:close-outlined />
          </NButton>
        </div>
        <h3 class="node-title">{{ selectedNode.label }}</h3>

        <NDivider style="margin: 12px 0" />

        <div class="related-section">
          <h4>关联节点 ({{ relatedNodes.length }})</h4>
          <div class="related-list">
            <div
              v-for="item in relatedNodes"
              :key="item.node.id"
              class="related-item"
              @click="selectedNode = item.node"
            >
              <span class="dot" :class="item.node.type" />
              <span class="related-label">{{ item.node.label }}</span>
              <NTag size="small" :bordered="false">{{ item.relation }}</NTag>
            </div>
          </div>
        </div>

        <NDivider style="margin: 12px 0" />

        <div class="actions">
          <NButton size="small" block>
            <template #icon>
              <icon-ant-design:file-text-outlined />
            </template>
            查看源文档
          </NButton>
        </div>
      </div>
    </div>

    <!-- 统计信息 -->
    <div class="stats-bar">
      <span>节点: {{ graphData.nodes.length }}</span>
      <span>关系: {{ graphData.edges.length }}</span>
      <span v-if="filteredNodes.length !== graphData.nodes.length">
        筛选结果: {{ filteredNodes.length }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.knowledge-graph-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--n-color);
  border-radius: 16px;
  overflow: hidden;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--n-border-color);
  flex-shrink: 0;
}

.legend {
  display: flex;
  gap: 16px;
  margin-left: auto;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--n-text-color-3);
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.dot.document { background: #667eea; }
.dot.person { background: #f6ad55; }
.dot.organization { background: #fc8181; }
.dot.concept { background: #4fd1c5; }
.dot.entity { background: #f093fb; }

.main-content {
  display: flex;
  flex: 1;
  min-height: 0;
}

.graph-container {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.graph-container canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.info-panel {
  width: 280px;
  padding: 16px;
  border-left: 1px solid var(--n-border-color);
  overflow-y: auto;
  flex-shrink: 0;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.node-type {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  text-transform: uppercase;
  color: #fff;
}

.node-type.document { background: #667eea; }
.node-type.person { background: #f6ad55; }
.node-type.organization { background: #fc8181; }
.node-type.concept { background: #4fd1c5; }
.node-type.entity { background: #f093fb; }

.node-title {
  font-size: 18px;
  margin: 8px 0 0;
}

.related-section h4 {
  font-size: 13px;
  color: var(--n-text-color-3);
  margin: 0 0 8px;
}

.related-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.related-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}

.related-item:hover {
  background: var(--n-color-hover);
}

.related-label {
  flex: 1;
  font-size: 13px;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stats-bar {
  display: flex;
  gap: 16px;
  padding: 8px 16px;
  border-top: 1px solid var(--n-border-color);
  font-size: 12px;
  color: var(--n-text-color-3);
  flex-shrink: 0;
}
</style>
