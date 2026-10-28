<script setup>
// 模板列表表格（v2）：过滤器 / 断言 / 结构化模板共用
defineProps({
  rows: { type: Array, default: () => [] },
})
const emit = defineEmits(['copy', 'edit', 'remove'])

const SCOPE_TEXT = { ROUTE: '路由级', GLOBAL: '全局', BOTH: '两者' }
const builtinTag = (b) => (b === 1 ? { text: '内置', type: 'info' } : { text: '自定义', type: 'success' })
</script>

<template>
  <el-table :data="rows" border size="small">
    <el-table-column prop="displayName" label="显示名" min-width="130">
      <template #default="{ row }">
        <span class="font-semibold">{{ row.displayName }}</span>
        <div class="text-xs text-gray-400">{{ row.name }}</div>
      </template>
    </el-table-column>
    <el-table-column prop="category" label="分类" width="110" />
    <el-table-column label="作用域" width="90">
      <template #default="{ row }"><el-tag size="small">{{ SCOPE_TEXT[row.scope] || row.scope }}</el-tag></template>
    </el-table-column>
    <el-table-column label="内置" width="80">
      <template #default="{ row }"><el-tag :type="builtinTag(row.builtin).type" size="small">{{ builtinTag(row.builtin).text }}</el-tag></template>
    </el-table-column>
    <el-table-column label="配置项" width="80">
      <template #default="{ row }">{{ (row.fields || []).length }}</template>
    </el-table-column>
    <el-table-column label="值类型" min-width="180" show-overflow-tooltip>
      <template #default="{ row }">
        <el-tag v-for="f in (row.fields || []).slice(0, 6)" :key="f.keyName" size="small" class="mr-1">
          {{ f.keyName }}:{{ f.valueTypeName || f.valueTypeId }}
        </el-tag>
        <span v-if="(row.fields || []).length > 6" class="text-xs text-gray-400">+{{ (row.fields || []).length - 6 }}</span>
      </template>
    </el-table-column>
    <el-table-column prop="remark" label="说明" min-width="200" show-overflow-tooltip />
    <el-table-column label="操作" width="200" fixed="right">
      <template #default="{ row }">
        <el-button size="small" @click="emit('copy', row)">复制</el-button>
        <el-button size="small" :disabled="row.builtin === 1" @click="emit('edit', row)">编辑</el-button>
        <el-button size="small" type="danger" :disabled="row.builtin === 1" @click="emit('remove', row)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>
