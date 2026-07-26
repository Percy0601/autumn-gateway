import { DrawerForm, ProFormText, ProFormDigit, ProFormSelect, ProFormSwitch, ProTable } from '@ant-design/pro-components';
import { Button, Popconfirm, message, Tag } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ProColumns, ActionType } from '@ant-design/pro-components';
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
};

const PERM_TYPES = ['MENU', 'API', 'BUTTON', 'DATA'];
const MATCH_TYPES = ['exact', 'prefix', 'suffix'];
const HTTP_METHODS = ['ALL', 'GET', 'POST', 'PUT', 'DELETE', 'PATCH'];

export default () => {
    const actionRef = useRef<ActionType>();
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [currentRow, setCurrentRow] = useState<Permission | undefined>(undefined);
    const [applications, setApplications] = useState<any[]>([]);

    useEffect(() => {
        request<{ data: any[] }>('/api/system/app/list').then(res => {
            setApplications(res.data || []);
        });
    }, []);

    const appOptions = useMemo(() =>
        applications.map(app => ({ label: `${app.name} (${app.appid})`, value: app.id })),
        [applications]
    );

    const columns: ProColumns<Permission>[] = [
        { title: 'ID', dataIndex: 'id', search: false, width: 60 },
        {
            title: '所属应用',
            dataIndex: 'appId',
            valueType: 'select',
            fieldProps: { options: appOptions, allowClear: true, placeholder: '全部' },
            render: (_, record) => {
                const app = applications.find(a => a.id === record.appId);
                return app ? `${app.name} (${app.appid})` : '-';
            },
        },
        {
            title: '类型',
            dataIndex: 'permType',
            width: 70,
            valueType: 'select',
            fieldProps: { options: PERM_TYPES.map(t => ({ label: t, value: t })), allowClear: true },
        },
        { title: '权限编码', dataIndex: 'code', copyable: true, ellipsis: true },
        { title: '权限名称', dataIndex: 'name', ellipsis: true },
        {
            title: '资源路径',
            dataIndex: 'resourcePath',
            ellipsis: true,
            copyable: true,
            search: false,
            render: (_, record) => record.resourcePath || '-',
        },
        {
            title: 'HTTP方法',
            dataIndex: 'httpMethod',
            width: 80,
            search: false,
            render: (_, record) => (
                <Tag color="blue">{record.httpMethod || 'ALL'}</Tag>
            ),
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 70,
            valueEnum: { 1: { text: '正常', status: 'Success' }, 0: { text: '禁用', status: 'Error' } },
        },
        { title: '创建时间', dataIndex: 'createdAt', valueType: 'dateTime', search: false, width: 160 },
        {
            title: '操作',
            valueType: 'option',
            width: 160,
            render: (_, record) => [
                <a key="detail" onClick={() => history.push(`/system/permission/detail/${record.id}`)}>详情</a>,
                <a key="edit" onClick={() => { setCurrentRow(record); setDrawerOpen(true); }}>编辑</a>,
                record.status === 1
                    ? <Popconfirm key="dis" title="确定禁用？" onConfirm={async () => {
                        await request(`/api/system/permission/${record.id}/disable`, { method: 'PUT' });
                        message.success('已禁用'); actionRef.current?.reload();
                    }}><a style={{ color: '#faad14' }}>禁用</a></Popconfirm>
                    : <Popconfirm key="en" title="确定启用？" onConfirm={async () => {
                        await request(`/api/system/permission/${record.id}/enable`, { method: 'PUT' });
                        message.success('已启用'); actionRef.current?.reload();
                    }}><a style={{ color: '#52c41a' }}>启用</a></Popconfirm>,
            ],
        },
    ];

    return (
        <>
            <ProTable<Permission>
                headerTitle="权限列表（含资源 URL 定义）"
                actionRef={actionRef}
                rowKey="id"
                request={async (params) => {
                    const res = await request<{ data: Permission[]; total: number }>('/api/system/permission', {
                        params: {
                            current: params.current,
                            pageSize: params.pageSize,
                            appId: params.appId,
                            permType: params.permType,
                            code: params.code,
                            name: params.name,
                        },
                    });
                    return { data: res.data, success: true, total: res.total };
                }}
                columns={columns}
                toolbar={{
                    actions: [
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
                search={{ labelWidth: 'auto' }}
            />

            <DrawerForm<Permission>
                title={currentRow ? '编辑权限' : '新增权限（同时定义资源 URL）'}
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
                    actionRef.current?.reload();
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
                {/* --- 资源 URL 定义 --- */}
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
                <ProFormDigit name="parentId" label="父级ID (菜单树)" min={0} fieldProps={{ precision: 0 }} placeholder="0=顶级" />
                <ProFormSwitch name="hidden" label="菜单隐藏" checkedChildren="是" unCheckedChildren="否" />
                {/* --- 通用 --- */}
                <ProFormSwitch
                    name="status"
                    label="状态"
                    checkedChildren="启用"
                    unCheckedChildren="禁用"
                />
            </DrawerForm>
        </>
    );
};
