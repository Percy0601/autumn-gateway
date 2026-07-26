import { DrawerForm, ProFormText, ProFormDigit, ProFormSelect, ProFormSwitch, ProTable } from '@ant-design/pro-components';
import { Button, Popconfirm, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ProColumns, ActionType } from '@ant-design/pro-components';
import { useRef, useState, useEffect, useMemo } from 'react';
import { request } from '@umijs/max';

type Resource = {
    id: number;
    appId: number;
    parentId: number;
    resType: string;
    matchType: string;
    name: string;
    action: string;
    icon?: string;
    sort: number;
    hidden: boolean;
    createdAt: string;
};

const RES_TYPES = ['MENU', 'API', 'BUTTON', 'PAGE_ELEMENT'];
const MATCH_TYPES = ['exact', 'prefix', 'suffix'];
const ACTIONS = ['ALL', 'GET', 'POST', 'PUT', 'DELETE', 'PATCH'];

export default () => {
    const actionRef = useRef<ActionType>();
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [currentRow, setCurrentRow] = useState<Resource | undefined>(undefined);
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

    const columns: ProColumns<Resource>[] = [
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
            title: '资源类型',
            dataIndex: 'resType',
            valueType: 'select',
            fieldProps: { options: RES_TYPES.map(t => ({ label: t, value: t })), allowClear: true },
        },
        {
            title: '匹配方式',
            dataIndex: 'matchType',
            valueType: 'select',
            fieldProps: { options: MATCH_TYPES.map(t => ({ label: t, value: t })), allowClear: true },
        },
        { title: '资源路径/名称', dataIndex: 'name', copyable: true, ellipsis: true },
        {
            title: 'HTTP方法',
            dataIndex: 'action',
            width: 80,
            valueEnum: Object.fromEntries(ACTIONS.map(a => [a, { text: a }])),
        },
        { title: '排序', dataIndex: 'sort', search: false, width: 60 },
        {
            title: '隐藏',
            dataIndex: 'hidden',
            search: false,
            width: 60,
            render: (_, record) => record.hidden ? '是' : '否',
        },
        { title: '创建时间', dataIndex: 'createdAt', valueType: 'dateTime', search: false },
        {
            title: '操作',
            valueType: 'option',
            render: (_, record) => [
                <a key="edit" onClick={() => { setCurrentRow(record); setDrawerOpen(true); }}>编辑</a>,
                <Popconfirm key="del" title="确定删除该资源？" onConfirm={async () => {
                    await request(`/api/system/resource/${record.id}`, { method: 'DELETE' });
                    message.success('已删除');
                    actionRef.current?.reload();
                }}><a style={{ color: '#ff4d4f' }}>删除</a></Popconfirm>,
            ],
        },
    ];

    return (
        <>
            <ProTable<Resource>
                headerTitle="资源列表"
                actionRef={actionRef}
                rowKey="id"
                request={async (params) => {
                    const res = await request<{ data: Resource[]; total: number }>('/api/system/resource', {
                        params: {
                            current: params.current,
                            pageSize: params.pageSize,
                            appId: params.appId,
                            resType: params.resType,
                            matchType: params.matchType,
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
                            新增资源
                        </Button>,
                    ],
                }}
                search={{ labelWidth: 'auto' }}
            />

            <DrawerForm<Resource>
                title={currentRow ? '编辑资源' : '新增资源'}
                width={480}
                open={drawerOpen}
                onOpenChange={setDrawerOpen}
                initialValues={
                    currentRow
                        ? { ...currentRow }
                        : { resType: 'API', matchType: 'exact', action: 'ALL', sort: 0, hidden: false, parentId: 0 }
                }
                onFinish={async (values) => {
                    const payload = { ...values, hidden: values.hidden ? 1 : 0 };
                    if (currentRow) {
                        await request(`/api/system/resource/${currentRow.id}`, { method: 'PUT', data: payload });
                        message.success('更新成功');
                    } else {
                        await request('/api/system/resource', { method: 'POST', data: payload });
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
                    name="resType"
                    label="资源类型"
                    rules={[{ required: true, message: '请选择资源类型' }]}
                    fieldProps={{ options: RES_TYPES.map(t => ({ label: t, value: t })) }}
                />
                <ProFormSelect
                    name="matchType"
                    label="匹配方式"
                    rules={[{ required: true, message: '请选择匹配方式' }]}
                    fieldProps={{ options: MATCH_TYPES.map(t => ({ label: t, value: t })) }}
                />
                <ProFormText
                    name="name"
                    label="资源路径/名称"
                    rules={[{ required: true, message: '请输入资源路径或名称' }]}
                    placeholder={resType => resType === 'API' ? '例如：/api/order/:id' : '例如：订单管理'}
                />
                <ProFormSelect
                    name="action"
                    label="HTTP方法"
                    fieldProps={{ options: ACTIONS.map(a => ({ label: a, value: a })) }}
                />
                <ProFormText name="icon" label="图标" placeholder="菜单图标名称" />
                <ProFormDigit name="sort" label="排序" min={0} fieldProps={{ precision: 0 }} />
                <ProFormDigit name="parentId" label="父资源ID" min={0} fieldProps={{ precision: 0 }} placeholder="0 表示顶级" />
                <ProFormSwitch name="hidden" label="隐藏" checkedChildren="是" unCheckedChildren="否" />
            </DrawerForm>
        </>
    );
};
