import React, { useState, useCallback, useEffect } from 'react';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Tabs, Descriptions, Tag, Button, Space, Spin, Transfer, message, Empty, Modal } from 'antd';
import { history, useParams, request } from '@umijs/max';

const RoleDetail: React.FC = () => {
    const params = useParams<{ id: string }>();
    const roleId = params.id;

    const [tab, setTab] = useState<string>('basic');
    const [loading, setLoading] = useState<boolean>(true);
    const [role, setRole] = useState<any>(null);
    const [error, setError] = useState<string | null>(null);

    // 权限相关
    const [allPermissions, setAllPermissions] = useState<any[]>([]);
    const [selectedPermissions, setSelectedPermissions] = useState<number[]>([]);
    const [permModalVisible, setPermModalVisible] = useState(false);
    const [tempSelectedPermissions, setTempSelectedPermissions] = useState<number[]>([]);

    // 用户相关
    const [allUsers, setAllUsers] = useState<any[]>([]);
    const [selectedUsers, setSelectedUsers] = useState<number[]>([]);
    const [userModalVisible, setUserModalVisible] = useState(false);
    const [tempSelectedUsers, setTempSelectedUsers] = useState<number[]>([]);

    // 角色关系相关
    const [allRoles, setAllRoles] = useState<any[]>([]);
    const [selectedRelatedRoles, setSelectedRelatedRoles] = useState<number[]>([]);

    const fetchRoleData = useCallback(async () => {
        if (!roleId) {
            setError('缺少角色ID');
            setLoading(false);
            return;
        }
        setLoading(true);
        setError(null);

        try {
            // 1. 获取角色基本信息
            let roleData = null;
            try {
                const roleRes = await request<{ data: any }>(`/api/system/role/${roleId}`);
                roleData = roleRes.data;
                setRole(roleData);
            } catch (e) {
                setError('获取角色基本信息失败');
                setLoading(false);
                return;
            }

            // 2. 并行获取其他数据
            try {
                const [permsRes, rolePermsRes, usersRes, roleUsersRes, rolesRes, roleRelationsRes] = await Promise.all([
                    request<{ data: any[] }>('/api/system/permission/list'),
                    request<{ data: any[] }>(`/api/system/role/${roleId}/permissions`),
                    request<{ data: any[] }>('/api/system/user/list'),
                    request<{ data: any[] }>(`/api/system/role/${roleId}/users`),
                    request<{ data: any[] }>('/api/system/role/list'),
                    request<{ data: any[] }>(`/api/system/role/${roleId}/relations`),
                ]);

                setAllPermissions(permsRes.data || []);
                setSelectedPermissions((rolePermsRes.data || []).map((item: any) => item.permissionId));
                setAllUsers(usersRes.data || []);
                setSelectedUsers((roleUsersRes.data || []).map((item: any) => item.userId));
                setAllRoles(rolesRes.data || []);
                setSelectedRelatedRoles((roleRelationsRes.data || []).map((item: any) => item.relatedRoleId));
            } catch (e) {
                message.warning('部分关联数据加载失败');
            }
        } finally {
            setLoading(false);
        }
    }, [roleId]);

    useEffect(() => {
        fetchRoleData();
    }, [fetchRoleData]);

    // 权限编辑：打开 Modal 时暂存当前已选
    const openPermModal = () => {
        setTempSelectedPermissions([...selectedPermissions]);
        setPermModalVisible(true);
    };

    const confirmPermSave = async () => {
        await request(`/api/system/role/${roleId}/permissions`, {
            method: 'PUT',
            data: { permissionIds: tempSelectedPermissions },
        });
        setSelectedPermissions(tempSelectedPermissions);
        setPermModalVisible(false);
        message.success('权限分配已更新');
    };

    // 用户编辑：打开 Modal 时暂存当前已选
    const openUserModal = () => {
        setTempSelectedUsers([...selectedUsers]);
        setUserModalVisible(true);
    };

    const confirmUserSave = async () => {
        await request(`/api/system/role/${roleId}/users`, {
            method: 'PUT',
            data: { userIds: tempSelectedUsers },
        });
        setSelectedUsers(tempSelectedUsers);
        setUserModalVisible(false);
        message.success('用户分配已更新');
    };

    // 角色关系保存
    const saveRelations = async () => {
        await request(`/api/system/role/${roleId}/relations`, {
            method: 'PUT',
            data: { relatedRoleIds: selectedRelatedRoles },
        });
        message.success('角色关系已更新');
    };

    if (!roleId) return <Empty description="角色ID不能为空" />;

    if (loading) {
        return (
            <PageContainer header={{ title: '加载中...' }}>
                <div style={{ textAlign: 'center', padding: 100 }}><Spin size="large" /></div>
            </PageContainer>
        );
    }

    if (error) {
        return (
            <PageContainer header={{ title: '错误' }}>
                <div style={{ textAlign: 'center', padding: 50 }}>
                    <p style={{ color: 'red', fontSize: 18 }}>{error}</p>
                    <Button type="primary" onClick={() => history.push('/system/role')}>返回角色列表</Button>
                </div>
            </PageContainer>
        );
    }

    if (!role) {
        return (
            <PageContainer header={{ title: '角色不存在' }}>
                <div style={{ textAlign: 'center', padding: 50 }}>
                    <p>未找到该角色</p>
                    <Button type="primary" onClick={() => history.push('/system/role')}>返回角色列表</Button>
                </div>
            </PageContainer>
        );
    }

    const permColumns = [
        { title: 'ID', dataIndex: 'id', width: 60 },
        { title: '权限编码', dataIndex: 'code', ellipsis: true },
        { title: '权限名称', dataIndex: 'name' },
        { title: '状态', dataIndex: 'status', render: (s: number) => <Tag color={s===1?'green':'red'}>{s===1?'正常':'禁用'}</Tag> },
    ];

    const userColumns = [
        { title: 'ID', dataIndex: 'id', width: 60 },
        { title: '用户名', dataIndex: 'username' },
        { title: '昵称', dataIndex: 'nickname' },
        { title: '邮箱', dataIndex: 'email' },
        { title: '状态', dataIndex: 'status', render: (s: number) => <Tag color={s===1?'green':'red'}>{s===1?'正常':'禁用'}</Tag> },
    ];

    return (
        <PageContainer
            header={{
                title: `角色详情 - ${role.name || '未知'}`,
                extra: <Button onClick={() => history.push('/system/role')}>返回列表</Button>,
            }}
        >
            <Spin spinning={loading}>
                <Tabs activeKey={tab} onChange={setTab}>
                    {/* 基本信息 Tab */}
                    <Tabs.TabPane tab="基本信息" key="basic">
                        <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
                            <Descriptions column={2} bordered>
                                <Descriptions.Item label="ID">{role.id}</Descriptions.Item>
                                <Descriptions.Item label="角色编码">{role.code}</Descriptions.Item>
                                <Descriptions.Item label="角色名称">{role.name}</Descriptions.Item>
                                <Descriptions.Item label="所属应用">{role.appId}</Descriptions.Item>
                                <Descriptions.Item label="层级">{role.level}</Descriptions.Item>
                                <Descriptions.Item label="描述">{role.description || '-'}</Descriptions.Item>
                                <Descriptions.Item label="状态">
                                    <Tag color={role.status === 1 ? 'green' : 'red'}>{role.status === 1 ? '正常' : '禁用'}</Tag>
                                </Descriptions.Item>
                                <Descriptions.Item label="创建时间">{role.createdAt}</Descriptions.Item>
                                <Descriptions.Item label="更新时间">{role.updatedAt}</Descriptions.Item>
                            </Descriptions>
                        </div>
                    </Tabs.TabPane>

                    {/* 权限列表 Tab */}
                    <Tabs.TabPane tab="权限列表" key="permissions">
                        <ProTable
                            headerTitle="已分配的权限"
                            rowKey="id"
                            search={false}
                            toolBarRender={() => [
                                <Button key="edit" type="primary" onClick={openPermModal}>编辑权限</Button>,
                            ]}
                            request={async () => {
                                const permDetails = allPermissions.filter(p => selectedPermissions.includes(p.id));
                                return { data: permDetails, success: true, total: permDetails.length };
                            }}
                            columns={permColumns}
                            pagination={{ pageSize: 10 }}
                        />
                    </Tabs.TabPane>

                    {/* 用户列表 Tab */}
                    <Tabs.TabPane tab="用户列表" key="users">
                        <ProTable
                            headerTitle="拥有该角色的用户"
                            rowKey="id"
                            search={false}
                            toolBarRender={() => [
                                <Button key="edit" type="primary" onClick={openUserModal}>编辑用户</Button>,
                            ]}
                            request={async () => {
                                const userDetails = allUsers.filter(u => selectedUsers.includes(u.id));
                                return { data: userDetails, success: true, total: userDetails.length };
                            }}
                            columns={userColumns}
                            pagination={{ pageSize: 10 }}
                        />
                    </Tabs.TabPane>

                    {/* 角色关系 Tab */}
                    <Tabs.TabPane tab="角色关系" key="relations">
                        <Transfer
                            dataSource={allRoles.filter(r => r.id !== Number(roleId)).map(r => ({
                                key: r.id,
                                title: `${r.name} (${r.code})`,
                            }))}
                            targetKeys={selectedRelatedRoles}
                            onChange={setSelectedRelatedRoles}
                            render={item => item.title}
                            titles={['可选角色', '已选角色']}
                            showSearch
                            filterOption={(inputValue, item) =>
                                item.title.toLowerCase().includes(inputValue.toLowerCase())
                            }
                        />
                        <Button type="primary" onClick={saveRelations} style={{ marginTop: 16 }}>
                            保存角色关系
                        </Button>
                    </Tabs.TabPane>
                </Tabs>
            </Spin>

            {/* 编辑权限 Modal */}
            <Modal
                title="编辑权限"
                open={permModalVisible}
                onCancel={() => setPermModalVisible(false)}
                onOk={confirmPermSave}
                width={600}
            >
                <Transfer
                    dataSource={allPermissions.map(p => ({
                        key: p.id,
                        title: `${p.name} (${p.code})`,
                    }))}
                    targetKeys={tempSelectedPermissions}
                    onChange={setTempSelectedPermissions}
                    render={item => item.title}
                    titles={['可选权限', '已选权限']}
                    showSearch
                    filterOption={(inputValue, item) =>
                        item.title.toLowerCase().includes(inputValue.toLowerCase())
                    }
                />
            </Modal>

            {/* 编辑用户 Modal */}
            <Modal
                title="编辑用户"
                open={userModalVisible}
                onCancel={() => setUserModalVisible(false)}
                onOk={confirmUserSave}
                width={600}
            >
                <Transfer
                    dataSource={allUsers.map(u => ({
                        key: u.id,
                        title: `${u.username} (${u.nickname || '-'})`,
                    }))}
                    targetKeys={tempSelectedUsers}
                    onChange={setTempSelectedUsers}
                    render={item => item.title}
                    titles={['可选用户', '已选用户']}
                    showSearch
                    filterOption={(inputValue, item) =>
                        item.title.toLowerCase().includes(inputValue.toLowerCase())
                    }
                />
            </Modal>
        </PageContainer>
    );
};

export default RoleDetail;