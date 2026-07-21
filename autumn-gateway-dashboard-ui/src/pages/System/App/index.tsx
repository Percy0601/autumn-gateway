import { DrawerForm, ProFormText, ProFormSwitch, ProFormTextArea, ProTable } from '@ant-design/pro-components';
import { Button, Popconfirm, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ProColumns, ActionType } from '@ant-design/pro-components';
import { useRef, useState } from 'react';
import { request } from '@umijs/max';

type Application = {
    id: number;
    appid: string;
    name: string;
    base_path?: string;
    description?: string;
    status: number;
    created_at: string;
};

export default () => {
    const actionRef = useRef<ActionType>();
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [currentRow, setCurrentRow] = useState<Application | undefined>(undefined);

    const columns: ProColumns<Application>[] = [
        { title: 'ID', dataIndex: 'id', search: false, width: 60 },
        { title: '应用标识', dataIndex: 'appid', copyable: true, ellipsis: true },
        { title: '应用名称', dataIndex: 'name', ellipsis: true },
        { title: 'API 前缀', dataIndex: 'base_path', hideInSearch: true },
        { title: '描述', dataIndex: 'description', hideInSearch: true, ellipsis: true },
        {
            title: '状态',
            dataIndex: 'status',
            valueEnum: {
                1: { text: '正常', status: 'Success' },
                0: { text: '禁用', status: 'Error' },
            },
        },
        { title: '创建时间', dataIndex: 'created_at', valueType: 'dateTime', search: false },
        {
            title: '操作',
            valueType: 'option',
            render: (_, record) => [
                <a key="edit" onClick={() => { setCurrentRow(record); setDrawerOpen(true); }}>编辑</a>,
                record.status === 1
                    ? <Popconfirm key="dis" title="确定禁用？" onConfirm={async () => {
                        await request(`/api/system/app/${record.id}/disable`, { method: 'PUT' });
                        message.success('已禁用'); actionRef.current?.reload();
                    }}><a style={{ color: '#faad14' }}>禁用</a></Popconfirm>
                    : <Popconfirm key="en" title="确定启用？" onConfirm={async () => {
                        await request(`/api/system/app/${record.id}/enable`, { method: 'PUT' });
                        message.success('已启用'); actionRef.current?.reload();
                    }}><a style={{ color: '#52c41a' }}>启用</a></Popconfirm>,
            ],
        },
    ];

    return (
        <>
            <ProTable<Application>
                headerTitle="应用列表"
                actionRef={actionRef}
                rowKey="id"
                request={async (params) => {
                    const res = await request<{ data: Application[]; total: number }>(
                        '/api/system/app',
                        { params: { current: params.current, pageSize: params.pageSize, appid: params.appid, name: params.name } }
                    );
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
                            新增应用
                        </Button>,
                    ],
                }}
                search={{ labelWidth: 'auto' }}
            />

            <DrawerForm<Application>
                title={currentRow ? '编辑应用' : '新增应用'}
                width={400}
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
                        await request(`/api/system/app/${currentRow.id}`, { method: 'PUT', data: payload });
                        message.success('更新成功');
                    } else {
                        await request('/api/system/app', { method: 'POST', data: payload });
                        message.success('创建成功');
                    }
                    setDrawerOpen(false);
                    actionRef.current?.reload();
                    return true;
                }}
            >
                <ProFormText
                    name="appid"
                    label="应用标识"
                    disabled={!!currentRow}
                    rules={[
                        { required: true, message: '请输入应用标识' },
                        { pattern: /^[a-z][a-z0-9_-]{2,31}$/, message: '以小写字母开头，3-32位' },
                    ]}
                    placeholder="例如：order"
                />
                <ProFormText
                    name="name"
                    label="应用名称"
                    rules={[{ required: true, message: '请输入应用名称' }]}
                    placeholder="例如：订单系统"
                />
                <ProFormText
                    name="base_path"
                    label="API 前缀"
                    placeholder="例如：/api/order"
                    rules={[{
                        pattern: /^\/[a-zA-Z][\w/-]*$/,
                        message: '必须以 / 开头，例如 /api/order',
                    }]}
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