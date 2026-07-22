import { DrawerForm, ProFormText, ProFormSwitch, ProFormTextArea, ProTable } from '@ant-design/pro-components';
import { Button, Popconfirm, message, Avatar, Tag } from 'antd';
import { PlusOutlined, UserOutlined } from '@ant-design/icons';
import type { ProColumns, ActionType } from '@ant-design/pro-components';
import { useRef, useState } from 'react';
import { request, history } from '@umijs/max';

type User = {
    id: number;
    username: string;
    nickname?: string;
    avatar?: string;
    email?: string;
    phone?: string;
    empNo?: string;
    status: number;
    lastLoginAt?: string;
    createdAt: string;
};

export default () => {
    const actionRef = useRef<ActionType>();
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [currentRow, setCurrentRow] = useState<User | undefined>(undefined);

    const columns: ProColumns<User>[] = [
        { title: 'ID', dataIndex: 'id', search: false, width: 60 },
        {
            title: '用户名',
            dataIndex: 'username',
            copyable: true,
            ellipsis: true,
        },
        {
            title: '昵称',
            dataIndex: 'nickname',
            ellipsis: true,
            render: (_, record) => (
                <span>
          <Avatar src={record.avatar} icon={<UserOutlined />} size="small" style={{ marginRight: 8 }} />
                    {record.nickname || '-'}
        </span>
            ),
        },
        { title: '邮箱', dataIndex: 'email', search: false, ellipsis: true },
        { title: '手机', dataIndex: 'phone', search: false, ellipsis: true },
        { title: '员工号', dataIndex: 'empNo', search: false, ellipsis: true },
        {
            title: '状态',
            dataIndex: 'status',
            valueEnum: {
                1: { text: '正常', status: 'Success' },
                0: { text: '禁用', status: 'Error' },
            },
        },
        { title: '最后登录', dataIndex: 'lastLoginAt', valueType: 'dateTime', search: false },
        {
            title: '操作',
            valueType: 'option',
            render: (_, record) => [
                <a key="detail" onClick={() => history.push(`/system/user/detail/${record.id}`)}>详情</a>,
                <a key="edit" onClick={() => { setCurrentRow(record); setDrawerOpen(true); }}>编辑</a>,
                // ... 禁用/启用 同之前
            ],
        },
    ];

    return (
        <>
            <ProTable<User>
                headerTitle="用户列表"
                actionRef={actionRef}
                rowKey="id"
                request={async (params) => {
                    const res = await request<{ data: User[]; total: number }>('/api/system/user', {
                        params: {
                            current: params.current,
                            pageSize: params.pageSize,
                            username: params.username,
                            nickname: params.nickname,
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
                            新增用户
                        </Button>,
                    ],
                }}
                search={{ labelWidth: 'auto' }}
            />

            <DrawerForm<User>
                title={currentRow ? '编辑用户' : '新增用户'}
                width={450}
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
                        await request(`/api/system/user/${currentRow.id}`, { method: 'PUT', data: payload });
                        message.success('更新成功');
                    } else {
                        await request('/api/system/user', { method: 'POST', data: payload });
                        message.success('创建成功');
                    }
                    setDrawerOpen(false);
                    actionRef.current?.reload();
                    return true;
                }}
            >
                <ProFormText
                    name="username"
                    label="用户名"
                    disabled={!!currentRow}
                    rules={[
                        { required: true, message: '请输入用户名' },
                        { pattern: /^[a-zA-Z][a-zA-Z0-9_]{2,31}$/, message: '以字母开头，只允许字母、数字、下划线，3-32位' },
                    ]}
                    placeholder="例如：admin"
                />
                <ProFormText
                    name="nickname"
                    label="昵称"
                    placeholder="例如：管理员"
                />
                <ProFormText
                    name="avatar"
                    label="头像 URL"
                    placeholder="https://example.com/avatar.png"
                />
                <ProFormText
                    name="email"
                    label="邮箱"
                    rules={[{ type: 'email', message: '请输入正确的邮箱格式' }]}
                    placeholder="admin@example.com"
                />
                <ProFormText
                    name="phone"
                    label="手机号"
                    placeholder="13800138000"
                />
                <ProFormText
                    name="empNo"
                    label="员工号"
                    placeholder="EMP-001"
                />
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