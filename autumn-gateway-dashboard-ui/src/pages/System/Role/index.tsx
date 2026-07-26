import { DrawerForm, ProFormText, ProFormDigit, ProFormSwitch, ProFormTextArea, ProFormSelect, ProTable } from '@ant-design/pro-components';
import { Button, Popconfirm, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ProColumns, ActionType } from '@ant-design/pro-components';
import { useRef, useState, useMemo, useEffect } from 'react';
import { request, history } from '@umijs/max';

type Role = {
    id: number;
    appId: number;
    code: string;
    name: string;
    level: number;
    description?: string;
    status: number;
    createdAt: string;
    updatedAt: string;
};

export default () => {
    const actionRef = useRef<ActionType>();
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [currentRow, setCurrentRow] = useState<Role | undefined>(undefined);
    const [applications, setApplications] = useState<any[]>([]);

    // 加载应用列表
    useEffect(() => {
        request<{ data: any[] }>('/api/system/app/list').then(res => {
            setApplications(res.data || []);
        });
    }, []);

    const appOptions = useMemo(() =>
            applications.map(app => ({ label: `${app.name} (${app.appid})`, value: app.id })),
        [applications]
    );

    const columns: ProColumns<Role>[] = [
        { title: 'ID', dataIndex: 'id', search: false, width: 70 },
        { title: '角色编码', dataIndex: 'code', copyable: true, ellipsis: true },
        { title: '角色名称', dataIndex: 'name', ellipsis: true },
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
        { title: '层级', dataIndex: 'level', search: false },
        {
            title: '状态',
            dataIndex: 'status',
            valueEnum: {
                1: { text: '正常', status: 'Success' },
                0: { text: '禁用', status: 'Error' },
            },
        },
        { title: '创建时间', dataIndex: 'createdAt', valueType: 'dateTime', search: false },
        {
            title: '操作',
            valueType: 'option',
            render: (_, record) => [
                <a key="detail" onClick={() => history.push(`/system/role/detail/${record.id}`)}>详情</a>,
                <a key="edit" onClick={() => { setCurrentRow(record); setDrawerOpen(true); }}>编辑</a>,
                record.status === 1
                    ? <Popconfirm key="disable" title="确定禁用该角色？" onConfirm={async () => {
                        await request(`/api/system/role/${record.id}/disable`, { method: 'PUT' });
                        message.success('已禁用');
                        actionRef.current?.reload();
                    }}><a style={{ color: '#faad14' }}>禁用</a></Popconfirm>
                    : <Popconfirm key="enable" title="确定启用该角色？" onConfirm={async () => {
                        await request(`/api/system/role/${record.id}/enable`, { method: 'PUT' });
                        message.success('已启用');
                        actionRef.current?.reload();
                    }}><a style={{ color: '#52c41a' }}>启用</a></Popconfirm>,
            ],
        },
    ];

    return (
        <>
            <ProTable<Role>
                headerTitle="角色列表"
                actionRef={actionRef}
                rowKey="id"
                request={async (params) => {
                    const res = await request<{ data: Role[]; total: number }>('/api/system/role', {
                        params: {
                            current: params.current,
                            pageSize: params.pageSize,
                            appId: params.appId,
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
                            新增角色
                        </Button>,
                    ],
                }}
                search={{ labelWidth: 'auto' }}
            />

            <DrawerForm<Role>
                title={currentRow ? '编辑角色' : '新增角色'}
                width={500}
                open={drawerOpen}
                onOpenChange={setDrawerOpen}
                initialValues={
                    currentRow
                        ? { ...currentRow, status: currentRow.status === 1 }
                        : { status: true, level: 0 }
                }
                onFinish={async (values) => {
                    const payload = { ...values, status: values.status ? 1 : 0 };
                    if (currentRow) {
                        await request(`/api/system/role/${currentRow.id}`, { method: 'PUT', data: payload });
                        message.success('更新成功');
                    } else {
                        await request('/api/system/role', { method: 'POST', data: payload });
                        message.success('创建成功');
                    }
                    setDrawerOpen(false);
                    actionRef.current?.reload();
                    return true;
                }}
            >
                {/* 新增：所属应用选择 */}
                <ProFormSelect
                    name="appId"
                    label="所属应用"
                    rules={[{ required: true, message: '请选择所属应用' }]}
                    fieldProps={{ options: appOptions }}
                    placeholder="请选择应用"
                />
                <ProFormText
                    name="code"
                    label="角色编码"
                    disabled={!!currentRow}
                    rules={[
                        { required: true, message: '请输入角色编码' },
                        { pattern: /^[a-zA-Z][a-zA-Z0-9_]{2,31}$/, message: '以字母开头，只允许字母、数字、下划线，3-32位' },
                    ]}
                    placeholder="例如：admin"
                />
                <ProFormText
                    name="name"
                    label="角色名称"
                    rules={[{ required: true, message: '请输入角色名称' }]}
                    placeholder="例如：管理员"
                />
                <ProFormDigit
                    name="level"
                    label="角色层级"
                    min={0}
                    fieldProps={{ precision: 0 }}
                    placeholder="数值越大权限越高"
                />
                <ProFormTextArea name="description" label="描述" />
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