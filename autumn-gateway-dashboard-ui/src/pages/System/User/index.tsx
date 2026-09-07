import { DrawerForm, ProFormText, ProFormSwitch, ProTable } from '@ant-design/pro-components';
import { Button, Popconfirm, message, Avatar, Modal, Input } from 'antd';
import { PlusOutlined, UserOutlined, KeyOutlined } from '@ant-design/icons';
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

    // 重置密码
    const [pwdModalOpen, setPwdModalOpen] = useState(false);
    const [pwdTarget, setPwdTarget] = useState<User | null>(null);
    const [newPassword, setNewPassword] = useState('');

    const openPwdModal = (record: User) => {
        setPwdTarget(record);
        setNewPassword('');
        setPwdModalOpen(true);
    };

    const handleResetPassword = async () => {
        if (!newPassword || newPassword.length < 6) {
            message.warning('密码长度至少6位');
            return;
        }
        try {
            await request(`/api/system/user/${pwdTarget!.id}/reset-password`, {
                method: 'PUT',
                data: { password: newPassword },
            });
            message.success(`已重置 ${pwdTarget!.username} 的密码`);
            setPwdModalOpen(false);
        } catch {
            message.error('重置密码失败');
        }
    };

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
            width: 220,
            render: (_, record) => [
                <a key="detail" onClick={() => history.push(`/system/user/detail/${record.id}`)}>详情</a>,
                <a key="edit" onClick={() => { setCurrentRow(record); setDrawerOpen(true); }}>编辑</a>,
                <a key="pwd" onClick={() => openPwdModal(record)} style={{ color: '#fa8c16' }}>
                    <KeyOutlined /> 重置密码
                </a>,
                record.status === 1
                    ? <Popconfirm key="disable" title="确定禁用？" onConfirm={async () => {
                        await request(`/api/system/user/${record.id}/disable`, { method: 'PUT' });
                        message.success('已禁用'); actionRef.current?.reload();
                    }}><a style={{ color: '#faad14' }}>禁用</a></Popconfirm>
                    : <Popconfirm key="enable" title="确定启用？" onConfirm={async () => {
                        await request(`/api/system/user/${record.id}/enable`, { method: 'PUT' });
                        message.success('已启用'); actionRef.current?.reload();
                    }}><a style={{ color: '#52c41a' }}>启用</a></Popconfirm>,
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
                <ProFormText name="nickname" label="昵称" placeholder="例如：管理员" />
                <ProFormText name="avatar" label="头像 URL" placeholder="https://example.com/avatar.png" />
                <ProFormText
                    name="email" label="邮箱"
                    rules={[{ type: 'email', message: '请输入正确的邮箱格式' }]}
                    placeholder="admin@example.com"
                />
                <ProFormText name="phone" label="手机号" placeholder="13800138000" />
                <ProFormText name="empNo" label="员工号" placeholder="EMP-001" />
                <ProFormSwitch name="status" label="状态" checkedChildren="启用" unCheckedChildren="禁用" />
            </DrawerForm>

            {/* 重置密码弹窗 */}
            <Modal
                title={`重置密码 — ${pwdTarget?.username}`}
                open={pwdModalOpen}
                onOk={handleResetPassword}
                onCancel={() => setPwdModalOpen(false)}
                okText="确认重置"
                cancelText="取消"
                destroyOnClose
            >
                <div style={{ marginBottom: 8 }}>
                    为用户 <strong>{pwdTarget?.username}</strong> 设置新密码：
                </div>
                <Input.Password
                    value={newPassword}
                    onChange={e => setNewPassword(e.target.value)}
                    placeholder="请输入新密码（至少6位）"
                    minLength={6}
                    onPressEnter={handleResetPassword}
                />
            </Modal>
        </>
    );
};
