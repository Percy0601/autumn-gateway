import { DrawerForm, ProFormText, ProFormSwitch, ProFormTextArea, ProFormSelect, ProTable } from '@ant-design/pro-components';
import { Button, Popconfirm, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ProColumns, ActionType } from '@ant-design/pro-components';
import { useRef, useState, useEffect, useMemo } from 'react';
import { request } from '@umijs/max';

type Permission = {
    id: number;
    appId: number;
    code: string;
    name: string;
    description?: string;
    status: number;
    createdAt: string;
};

type Application = {
    id: number;
    appid: string;
    name: string;
};

export default () => {
    const actionRef = useRef<ActionType>();
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [currentRow, setCurrentRow] = useState<Permission | undefined>(undefined);
    const [applications, setApplications] = useState<Application[]>([]);

    // 加载应用列表
    useEffect(() => {
        request<{ data: Application[] }>('/api/system/app/list').then(res => {
            setApplications(res.data || []);
        });
    }, []);

    // 应用选项
    const appOptions = useMemo(() =>
            applications.map(app => ({ label: `${app.name} (${app.appid})`, value: app.id })),
        [applications]
    );

    const columns: ProColumns<Permission>[] = useMemo(() => [
        { title: 'ID', dataIndex: 'id', search: false, width: 50 },
        {
            title: '所属应用',
            dataIndex: 'appId',
            valueType: 'select',
            fieldProps: { options: appOptions },
            render: (_, record) => {
                const app = applications.find(a => a.id === record.appId);
                return app ? `${app.name} (${app.appid})` : '-';
            },
        },
        { title: '权限编码', dataIndex: 'code', copyable: true, ellipsis: true },
        { title: '权限名称', dataIndex: 'name', ellipsis: true },
        { title: '描述', dataIndex: 'description', search: false, ellipsis: true },
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
    ], [appOptions, applications]);

    return (
        <>
            <ProTable<Permission>
                headerTitle="权限列表"
                actionRef={actionRef}
                rowKey="id"
                request={async (params) => {
                    const res = await request<{ data: Permission[]; total: number }>('/api/system/permission', {
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
                            新增权限
                        </Button>,
                    ],
                }}
                search={{ labelWidth: 'auto' }}
            />

            <DrawerForm<Permission>
                title={currentRow ? '编辑权限' : '新增权限'}
                width={420}
                open={drawerOpen}
                onOpenChange={setDrawerOpen}
                initialValues={
                    currentRow
                        ? { ...currentRow, status: currentRow.status === 1 }
                        : { status: true }
                }
                onFinish={async (values) => {
                    const payload = { ...values, status: values.status ? 1 : 0 };
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
                <ProFormText
                    name="code"
                    label="权限编码"
                    disabled={!!currentRow}
                    rules={[
                        { required: true, message: '请输入权限编码' },
                        { pattern: /^[a-z][a-z0-9_:]{2,63}$/, message: '以小写字母开头，允许小写字母、数字、冒号、下划线，3-64位' },
                    ]}
                    placeholder="例如：app:create"
                />
                <ProFormText
                    name="name"
                    label="权限名称"
                    rules={[{ required: true, message: '请输入权限名称' }]}
                    placeholder="例如：创建应用"
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