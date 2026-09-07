import { DrawerForm, ProFormText, ProFormDigit, ProFormSelect, ProFormSwitch, ProFormTreeSelect, ProTable } from '@ant-design/pro-components';
import { Button, Popconfirm, message, Tag, Input, Select, Space } from 'antd';
import { PlusOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import { useRef, useState, useEffect, useMemo } from 'react';
import { request, history } from '@umijs/max';

type Permission = {
    id: number;
    appId: number;
    category?: string;
    code: string;
    name: string;
    permType: string;
    resourcePath?: string;
    httpMethod: string;
    matchType: string;
    parentId: number;
    icon?: string;
    sort: number;
    hidden: number;
    description?: string;
    status: number;
    createdAt: string;
    children?: Permission[];
};

const PERM_TYPES = ['MENU', 'API', 'BUTTON', 'DATA'];
const MATCH_TYPES = ['exact', 'prefix', 'suffix'];
const HTTP_METHODS = ['ALL', 'GET', 'POST', 'PUT', 'DELETE', 'PATCH'];

// 从扁平列表构建树
const buildTree = (list: Permission[]): Permission[] => {
    const map = new Map<number, Permission>();
    const roots: Permission[] = [];

    // 先全部浅拷贝
    list.forEach(item => {
        map.set(item.id, { ...item, children: [] });
    });

    // 建立父子关系
    map.forEach(item => {
        if (item.parentId && map.has(item.parentId)) {
            map.get(item.parentId)!.children!.push(item);
        } else {
            roots.push(item);
        }
    });

    // 递归清理空的 children 数组 + 按 sort 排序
    const clean = (nodes: Permission[]): Permission[] => {
        return nodes
            .sort((a, b) => (a.sort || 0) - (b.sort || 0))
            .map(node => ({
                ...node,
                children: node.children && node.children.length > 0 ? clean(node.children) : undefined,
            }));
    };

    return clean(roots);
};

// 递归过滤树
const filterTree = (nodes: Permission[], keyword: string, appId?: number, permType?: string): Permission[] => {
    return nodes.reduce<Permission[]>((acc, node) => {
        const matchApp = !appId || node.appId === appId;
        const matchType = !permType || node.permType === permType;
        const matchKeyword = !keyword ||
            node.name?.toLowerCase().includes(keyword.toLowerCase()) ||
            node.code?.toLowerCase().includes(keyword.toLowerCase()) ||
            node.resourcePath?.toLowerCase().includes(keyword.toLowerCase());

        const filteredChildren = node.children ? filterTree(node.children, keyword, appId, permType) : [];

        if ((matchApp && matchType && matchKeyword) || filteredChildren.length > 0) {
            acc.push({ ...node, children: filteredChildren.length > 0 ? filteredChildren : node.children });
        }
        return acc;
    }, []);
};

export default () => {
    const actionRef = useRef<any>();
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [currentRow, setCurrentRow] = useState<Permission | undefined>(undefined);
    const [applications, setApplications] = useState<any[]>([]);
    const [flatPermissions, setFlatPermissions] = useState<Permission[]>([]);
    const [loading, setLoading] = useState(false);

    // 筛选状态
    const [keyword, setKeyword] = useState('');
    const [filterAppId, setFilterAppId] = useState<number | undefined>();
    const [filterPermType, setFilterPermType] = useState<string | undefined>();

    const loadData = async () => {
        setLoading(true);
        try {
            const [appsRes, permsRes] = await Promise.all([
                request<{ data: any[] }>('/api/system/app/list'),
                request<{ data: Permission[] }>('/api/system/permission/list'),
            ]);
            setApplications(appsRes.data || []);
            setFlatPermissions(permsRes.data || []);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const appOptions = useMemo(() =>
        applications.map(app => ({ label: `${app.name} (${app.appid})`, value: app.id })),
        [applications]
    );

    // 构建权限树（用于表单中的父级选择）
    const buildPermTree = (excludeId?: number): any[] => {
        const buildNodes = (parentId: number): any[] => {
            return flatPermissions
                .filter(p => p.parentId === parentId && p.id !== excludeId)
                .map(p => ({
                    title: `${p.name} (${p.code})`,
                    value: p.id,
                    children: buildNodes(p.id),
                }));
        };
        return [{ title: '顶级（无父级）', value: 0, children: buildNodes(0) }];
    };

    // 构建用于展示的树 + 过滤
    const displayTree = useMemo(() => {
        const tree = buildTree(flatPermissions);
        return filterTree(tree, keyword, filterAppId, filterPermType);
    }, [flatPermissions, keyword, filterAppId, filterPermType]);

    const columns: ProColumns<Permission>[] = [
        {
            title: '权限名称',
            dataIndex: 'name',
            width: 200,
            ellipsis: true,
            render: (_, record) => (
                <Space>
                    <Tag color={record.permType === 'MENU' ? 'purple' : record.permType === 'API' ? 'blue' : record.permType === 'BUTTON' ? 'orange' : 'default'}>
                        {record.permType}
                    </Tag>
                    <span>{record.name}</span>
                </Space>
            ),
        },
        {
            title: '权限编码',
            dataIndex: 'code',
            width: 180,
            ellipsis: true,
            copyable: true,
        },
        {
            title: '所属应用',
            dataIndex: 'appId',
            width: 140,
            render: (_, record) => {
                const app = applications.find(a => a.id === record.appId);
                return app ? `${app.name} (${app.appid})` : '-';
            },
        },
        {
            title: '资源路径',
            dataIndex: 'resourcePath',
            width: 220,
            ellipsis: true,
            copyable: true,
            render: (_, record) => record.resourcePath ? <code>{record.resourcePath}</code> : '-',
        },
        {
            title: 'HTTP',
            dataIndex: 'httpMethod',
            width: 70,
            render: (_, record) => record.httpMethod && record.httpMethod !== 'ALL'
                ? <Tag color="green">{record.httpMethod}</Tag>
                : null,
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 60,
            render: (_, record) => (
                <Tag color={record.status === 1 ? 'green' : 'red'}>
                    {record.status === 1 ? '正常' : '禁用'}
                </Tag>
            ),
        },
        {
            title: '排序',
            dataIndex: 'sort',
            width: 50,
        },
        {
            title: '操作',
            valueType: 'option',
            width: 180,
            render: (_, record) => [
                <a key="detail" onClick={() => history.push(`/system/permission/detail/${record.id}`)}>详情</a>,
                <a key="edit" onClick={() => { setCurrentRow(record); setDrawerOpen(true); }}>编辑</a>,
                record.status === 1
                    ? <Popconfirm key="dis" title="确定禁用？" onConfirm={async () => {
                        await request(`/api/system/permission/${record.id}/disable`, { method: 'PUT' });
                        message.success('已禁用'); loadData();
                    }}><a style={{ color: '#faad14' }}>禁用</a></Popconfirm>
                    : <Popconfirm key="en" title="确定启用？" onConfirm={async () => {
                        await request(`/api/system/permission/${record.id}/enable`, { method: 'PUT' });
                        message.success('已启用'); loadData();
                    }}><a style={{ color: '#52c41a' }}>启用</a></Popconfirm>,
            ],
        },
    ];

    return (
        <>
            <ProTable<Permission>
                headerTitle={
                    <Space>
                        <span>权限树</span>
                        <Tag>{flatPermissions.length} 项</Tag>
                    </Space>
                }
                actionRef={actionRef}
                rowKey="id"
                loading={loading}
                dataSource={displayTree}
                columns={columns}
                pagination={false}
                search={false}
                options={{ reload: loadData, density: true }}
                toolbar={{
                    title: (
                        <Space wrap>
                            <Input
                                placeholder="搜索名称/编码/路径"
                                prefix={<SearchOutlined />}
                                allowClear
                                style={{ width: 220 }}
                                value={keyword}
                                onChange={e => setKeyword(e.target.value)}
                            />
                            <Select
                                placeholder="所属应用"
                                allowClear
                                style={{ width: 160 }}
                                value={filterAppId}
                                onChange={setFilterAppId}
                                options={appOptions}
                            />
                            <Select
                                placeholder="权限类型"
                                allowClear
                                style={{ width: 110 }}
                                value={filterPermType}
                                onChange={setFilterPermType}
                                options={PERM_TYPES.map(t => ({ label: t, value: t }))}
                            />
                        </Space>
                    ),
                    actions: [
                        <Button key="reload" icon={<ReloadOutlined />} onClick={loadData}>刷新</Button>,
                        <Button
                            key="add"
                            type="primary"
                            icon={<PlusOutlined />}
                            onClick={() => { setCurrentRow(undefined); setDrawerOpen(true); }}
                        >
                            新增权限
                        </Button>,
                    ],
                }}
                expandable={{
                    defaultExpandAllRows: true,
                    indentSize: 24,
                }}
                scroll={{ x: 1200 }}
            />

            <DrawerForm<Permission>
                title={currentRow ? '编辑权限' : '新增权限'}
                width={520}
                open={drawerOpen}
                onOpenChange={setDrawerOpen}
                initialValues={
                    currentRow
                        ? { ...currentRow, status: currentRow.status === 1, hidden: currentRow.hidden === 1 }
                        : { status: true, permType: 'API', httpMethod: 'ALL', matchType: 'exact', sort: 0, hidden: false, parentId: 0 }
                }
                onFinish={async (values) => {
                    const payload = { ...values, status: values.status ? 1 : 0, hidden: values.hidden ? 1 : 0 };
                    if (currentRow) {
                        await request(`/api/system/permission/${currentRow.id}`, { method: 'PUT', data: payload });
                        message.success('更新成功');
                    } else {
                        await request('/api/system/permission', { method: 'POST', data: payload });
                        message.success('创建成功');
                    }
                    setDrawerOpen(false);
                    loadData();
                    return true;
                }}
            >
                <ProFormSelect
                    name="appId"
                    label="所属应用"
                    rules={[{ required: true, message: '请选择所属应用' }]}
                    fieldProps={{ options: appOptions }}
                    placeholder="请选择应用"
                />
                <ProFormSelect
                    name="permType"
                    label="权限类型"
                    rules={[{ required: true, message: '请选择类型' }]}
                    fieldProps={{ options: PERM_TYPES.map(t => ({ label: t, value: t })) }}
                />
                <ProFormText
                    name="code"
                    label="权限编码"
                    disabled={!!currentRow}
                    rules={[
                        { required: true, message: '请输入权限编码' },
                        { pattern: /^[a-z][a-z0-9_:]{2,63}$/, message: '以小写字母开头，3-64位' },
                    ]}
                    placeholder="例如：order:create"
                />
                <ProFormText
                    name="name"
                    label="权限名称"
                    rules={[{ required: true, message: '请输入权限名称' }]}
                    placeholder="例如：创建订单"
                />
                <ProFormText name="category" label="分类标签" placeholder="例如：订单管理" />
                <ProFormText
                    name="resourcePath"
                    label="资源路径 (URL)"
                    placeholder="API: /api/order/:id ｜ MENU: /order/list"
                />
                <ProFormSelect
                    name="httpMethod"
                    label="HTTP方法"
                    fieldProps={{ options: HTTP_METHODS.map(m => ({ label: m, value: m })) }}
                />
                <ProFormSelect
                    name="matchType"
                    label="匹配方式"
                    fieldProps={{ options: MATCH_TYPES.map(m => ({ label: m, value: m })) }}
                />
                <ProFormDigit name="sort" label="排序" min={0} fieldProps={{ precision: 0 }} />
                <ProFormTreeSelect
                    name="parentId"
                    label="父级权限"
                    placeholder="选择父级（空=顶级）"
                    fieldProps={{
                        treeData: buildPermTree(currentRow?.id),
                        treeDefaultExpandAll: false,
                        showSearch: true,
                        treeNodeFilterProp: 'title',
                        allowClear: true,
                        dropdownStyle: { maxHeight: 400, overflow: 'auto' },
                    }}
                />
                <ProFormSwitch name="hidden" label="菜单隐藏" checkedChildren="是" unCheckedChildren="否" />
                <ProFormSwitch name="status" label="状态" checkedChildren="启用" unCheckedChildren="禁用" />
            </DrawerForm>
        </>
    );
};
