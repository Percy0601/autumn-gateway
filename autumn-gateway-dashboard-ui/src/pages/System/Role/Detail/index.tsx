import React, { useState, useCallback, useEffect } from 'react';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Tabs, Descriptions, Tag, Button, Spin, message, Empty } from 'antd';
import { history, useParams, request } from '@umijs/max';

const PAGE_SIZE_LARGE = 9999;

const RoleDetail: React.FC = () => {
    const params = useParams<{ id: string }>();
    const roleId = params.id;

    const [tab, setTab] = useState<string>('basic');
    const [loading, setLoading] = useState<boolean>(true);
    const [role, setRole] = useState<any>(null);

    // 应用名称映射
    const [appMap, setAppMap] = useState<Record<number, string>>({});

    // 权限相关
    const [allPermissions, setAllPermissions] = useState<any[]>([]);
    const [selectedPermissionIds, setSelectedPermissionIds] = useState<number[]>([]);

    // 用户相关
    const [allUsers, setAllUsers] = useState<any[]>([]);
    const [selectedUserIds, setSelectedUserIds] = useState<number[]>([]);

    const fetchRoleData = useCallback(async () => {
        if (!roleId) return;
        setLoading(true);
        try {
            // 第一步：获取角色基本信息和应用列表（必须成功）
            const [roleRes, appsRes] = await Promise.all([
                request<{ data: any }>(`/api/system/role/${roleId}`),
                request<{ data: any[] }>('/api/system/app/list'),
            ]);

            setRole(roleRes.data);

            // 构建应用名称映射
            const map: Record<number, string> = {};
            (appsRes.data || []).forEach((app: any) => {
                map[app.id] = app.name;
            });
            setAppMap(map);

            // 第二步：获取关联数据（允许部分失败）
            try {
                const [permsRes, rolePermsRes, usersRes, roleUsersRes] = await Promise.all([
                    request<{ data: any[] }>(`/api/system/permission?pageSize=${PAGE_SIZE_LARGE}`),
                    request<{ data: any[] }>(`/api/system/role/${roleId}/permissions`),
                    request<{ data: any[] }>(`/api/system/user?pageSize=${PAGE_SIZE_LARGE}`),
                    request<{ data: any[] }>(`/api/system/role/${roleId}/users`),
                ]);

                setAllPermissions(permsRes.data || []);
                // 后端返回完整的 Permission 对象列表
                setSelectedPermissionIds((rolePermsRes.data || []).map((item: any) => item.id));
                setAllUsers(usersRes.data || []);
                // 后端返回完整的 User 对象列表
                setSelectedUserIds((roleUsersRes.data || []).map((item: any) => item.id));
            } catch (e) {
                message.warning('部分关联数据加载失败，可切换页签重试');
            }
        } catch (error) {
            message.error('加载角色数据失败');
        } finally {
            setLoading(false);
        }
    }, [roleId]);

    useEffect(() => {
        if (roleId) {
            fetchRoleData();
        }
    }, [roleId, fetchRoleData]);

    // 切换权限关联状态
    const togglePermission = async (permId: number, currentlyAssociated: boolean) => {
        let newIds: number[];
        if (currentlyAssociated) {
            newIds = selectedPermissionIds.filter(id => id !== permId);
        } else {
            newIds = [...selectedPermissionIds, permId];
        }
        try {
            // 后端接收 List<Long>，直接发数组
            await request(`/api/system/role/${roleId}/permissions`, {
                method: 'PUT',
                data: newIds,
            });
            setSelectedPermissionIds(newIds);
            message.success(currentlyAssociated ? '已解绑权限' : '已关联权限');
        } catch {
            message.error('操作失败');
        }
    };

    // 切换用户关联状态
    const toggleUser = async (userId: number, currentlyAssociated: boolean) => {
        let newIds: number[];
        if (currentlyAssociated) {
            newIds = selectedUserIds.filter(id => id !== userId);
        } else {
            newIds = [...selectedUserIds, userId];
        }
        try {
            // 后端接收 List<Long>，直接发数组
            await request(`/api/system/role/${roleId}/users`, {
                method: 'PUT',
                data: newIds,
            });
            setSelectedUserIds(newIds);
            message.success(currentlyAssociated ? '已解绑用户' : '已关联用户');
        } catch {
            message.error('操作失败');
        }
    };

    if (!roleId) return <Empty description="角色ID不能为空" />;

    // 权限表格列
    const permColumns = [
        { title: '权限ID', dataIndex: 'id', width: 80 },
        { title: '权限编码', dataIndex: 'code', ellipsis: true },
        { title: '权限名称', dataIndex: 'name', ellipsis: true },
        {
            title: '所属应用',
            dataIndex: 'appId',
            render: (appId: number) => appMap[appId] || `应用#${appId}`,
        },
        {
            title: '权限类型',
            dataIndex: 'permType',
            width: 80,
            valueEnum: {
                MENU: { text: '菜单' },
                API: { text: 'API' },
                BUTTON: { text: '按钮' },
                DATA: { text: '数据' },
            },
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 70,
            render: (s: number) => <Tag color={s === 1 ? 'green' : 'red'}>{s === 1 ? '正常' : '禁用'}</Tag>,
        },
        {
            title: '关联状态',
            dataIndex: 'id',
            width: 80,
            render: (id: number) => {
                const isAssociated = selectedPermissionIds.includes(id);
                return <Tag color={isAssociated ? 'green' : 'default'}>{isAssociated ? '已关联' : '未关联'}</Tag>;
            },
        },
        {
            title: '操作',
            dataIndex: 'id',
            width: 80,
            render: (id: number) => {
                const isAssociated = selectedPermissionIds.includes(id);
                return (
                    <a onClick={() => togglePermission(id, isAssociated)} style={{ color: isAssociated ? '#ff4d4f' : '#1890ff' }}>
                        {isAssociated ? '解绑' : '关联'}
                    </a>
                );
            },
        },
    ];

    // 用户表格列
    const userColumns = [
        { title: '用户ID', dataIndex: 'id', width: 80 },
        { title: '用户名', dataIndex: 'username', ellipsis: true },
        { title: '昵称', dataIndex: 'nickname', ellipsis: true },
        { title: '邮箱', dataIndex: 'email', ellipsis: true },
        {
            title: '状态',
            dataIndex: 'status',
            width: 70,
            render: (s: number) => <Tag color={s === 1 ? 'green' : 'red'}>{s === 1 ? '正常' : '禁用'}</Tag>,
        },
        {
            title: '关联状态',
            dataIndex: 'id',
            width: 80,
            render: (id: number) => {
                const isAssociated = selectedUserIds.includes(id);
                return <Tag color={isAssociated ? 'green' : 'default'}>{isAssociated ? '已关联' : '未关联'}</Tag>;
            },
        },
        {
            title: '操作',
            dataIndex: 'id',
            width: 80,
            render: (id: number) => {
                const isAssociated = selectedUserIds.includes(id);
                return (
                    <a onClick={() => toggleUser(id, isAssociated)} style={{ color: isAssociated ? '#ff4d4f' : '#1890ff' }}>
                        {isAssociated ? '解绑' : '关联'}
                    </a>
                );
            },
        },
    ];

    return (
        <PageContainer
            header={{
                title: `角色详情 - ${role?.name || '加载中...'}`,
                extra: <Button onClick={() => history.push('/system/role')}>返回列表</Button>,
            }}
        >
            <Spin spinning={loading}>
                <Tabs activeKey={tab} onChange={setTab}>
                    {/* 基本信息 Tab */}
                    <Tabs.TabPane tab="基本信息" key="basic">
                        <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
                            {role ? (
                                <Descriptions column={2} bordered>
                                    <Descriptions.Item label="ID">{role.id}</Descriptions.Item>
                                    <Descriptions.Item label="角色编码">{role.code}</Descriptions.Item>
                                    <Descriptions.Item label="角色名称">{role.name}</Descriptions.Item>
                                    <Descriptions.Item label="所属应用">
                                        {appMap[role.appId] || `应用#${role.appId}`}
                                    </Descriptions.Item>
                                    <Descriptions.Item label="层级">{role.level}</Descriptions.Item>
                                    <Descriptions.Item label="描述">{role.description || '-'}</Descriptions.Item>
                                    <Descriptions.Item label="状态">
                                        <Tag color={role.status === 1 ? 'green' : 'red'}>
                                            {role.status === 1 ? '正常' : '禁用'}
                                        </Tag>
                                    </Descriptions.Item>
                                    <Descriptions.Item label="创建时间">{role.createdAt}</Descriptions.Item>
                                    <Descriptions.Item label="更新时间">{role.updatedAt}</Descriptions.Item>
                                </Descriptions>
                            ) : '暂无数据'}
                        </div>
                    </Tabs.TabPane>

                    {/* 关联权限 Tab */}
                    <Tabs.TabPane tab="关联权限" key="permissions">
                        <ProTable
                            headerTitle="权限列表"
                            rowKey="id"
                            search={false}
                            pagination={{ pageSize: 10 }}
                            request={async () => {
                                const data = allPermissions.map(perm => ({
                                    ...perm,
                                    associated: selectedPermissionIds.includes(perm.id),
                                }));
                                return { data, success: true, total: data.length };
                            }}
                            columns={permColumns}
                        />
                    </Tabs.TabPane>

                    {/* 关联用户 Tab */}
                    <Tabs.TabPane tab="关联用户" key="users">
                        <ProTable
                            headerTitle="用户列表"
                            rowKey="id"
                            search={false}
                            pagination={{ pageSize: 10 }}
                            request={async () => {
                                const data = allUsers.map(user => ({
                                    ...user,
                                    associated: selectedUserIds.includes(user.id),
                                }));
                                return { data, success: true, total: data.length };
                            }}
                            columns={userColumns}
                        />
                    </Tabs.TabPane>
                </Tabs>
            </Spin>
        </PageContainer>
    );
};

export default RoleDetail;
