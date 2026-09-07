import React, { useState, useCallback, useEffect } from 'react';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Tabs, Descriptions, Tag, Button, Space, Spin, message, Empty } from 'antd';
import { history, useParams, request } from '@umijs/max';

const UserDetail: React.FC = () => {
    const params = useParams<{ id: string }>();
    const userId = params.id;

    const [tab, setTab] = useState<string>('basic');
    const [loading, setLoading] = useState<boolean>(true);
    const [user, setUser] = useState<any>(null);

    // 应用相关
    const [allApps, setAllApps] = useState<any[]>([]);
    const [selectedAppIds, setSelectedAppIds] = useState<number[]>([]);

    // 角色相关
    const [allRoles, setAllRoles] = useState<any[]>([]);
    const [selectedRoleIds, setSelectedRoleIds] = useState<number[]>([]);
    const [appMap, setAppMap] = useState<Record<number, string>>({});

    const fetchUserData = useCallback(async () => {
        if (!userId) return;
        setLoading(true);
        try {
            const [userRes, appsRes, userAppsRes, rolesRes, userRolesRes] = await Promise.all([
                request<{ data: any }>(`/api/system/user/${userId}`),
                request<{ data: any[] }>('/api/system/app/list'),
                request<{ data: any[] }>(`/api/system/user/${userId}/apps`),
                request<{ data: any[] }>('/api/system/role/list'),
                request<{ data: any[] }>(`/api/system/user/${userId}/roles`),
            ]);

            setUser(userRes.data);
            setAllApps(appsRes.data || []);
            setSelectedAppIds((userAppsRes.data || []).map((item: any) => item.appId));
            setAllRoles(rolesRes.data || []);
            setSelectedRoleIds((userRolesRes.data || []).map((item: any) => item.roleId));

            // 构建应用名称映射
            const map: Record<number, string> = {};
            (appsRes.data || []).forEach((app: any) => {
                map[app.id] = app.name;
            });
            setAppMap(map);
        } catch (error) {
            message.error('加载用户数据失败');
        } finally {
            setLoading(false);
        }
    }, [userId]);

    useEffect(() => {
        if (userId) {
            fetchUserData();
        }
    }, [userId, fetchUserData]);

    // 切换应用关联状态
    const toggleApp = async (appId: number, currentlyAssociated: boolean) => {
        let newIds: number[];
        if (currentlyAssociated) {
            newIds = selectedAppIds.filter(id => id !== appId);
        } else {
            newIds = [...selectedAppIds, appId];
        }
        try {
            await request(`/api/system/user/${userId}/apps`, {
                method: 'PUT',
                data: { appIds: newIds },
            });
            setSelectedAppIds(newIds);
            message.success(currentlyAssociated ? '已解绑应用' : '已关联应用');
        } catch {
            message.error('操作失败');
        }
    };

    // 切换角色关联状态
    const toggleRole = async (roleId: number, currentlyAssociated: boolean) => {
        let newIds: number[];
        if (currentlyAssociated) {
            newIds = selectedRoleIds.filter(id => id !== roleId);
        } else {
            newIds = [...selectedRoleIds, roleId];
        }
        try {
            await request(`/api/system/user/${userId}/roles`, {
                method: 'PUT',
                data: { roleIds: newIds },
            });
            setSelectedRoleIds(newIds);
            message.success(currentlyAssociated ? '已解绑角色' : '已关联角色');
        } catch {
            message.error('操作失败');
        }
    };

    if (!userId) return <Empty description="用户ID不能为空" />;

    // 应用表格列
    const appColumns = [
        { title: '应用ID', dataIndex: 'id', width: 80 },
        { title: '应用名称', dataIndex: 'name', ellipsis: true },
        { title: '应用标识', dataIndex: 'appid', ellipsis: true },
        {
            title: '关联状态',
            dataIndex: 'id',
            render: (id: number) => {
                const isAssociated = selectedAppIds.includes(id);
                return <Tag color={isAssociated ? 'green' : 'default'}>{isAssociated ? '已关联' : '未关联'}</Tag>;
            },
        },
        {
            title: '操作',
            dataIndex: 'id',
            render: (id: number) => {
                const isAssociated = selectedAppIds.includes(id);
                return (
                    <a onClick={() => toggleApp(id, isAssociated)} style={{ color: isAssociated ? '#ff4d4f' : '#1890ff' }}>
                        {isAssociated ? '解绑' : '关联'}
                    </a>
                );
            },
        },
    ];

    // 角色表格列
    const roleColumns = [
        { title: '角色ID', dataIndex: 'id', width: 80 },
        { title: '角色编码', dataIndex: 'code', ellipsis: true },
        { title: '角色名称', dataIndex: 'name', ellipsis: true },
        {
            title: '所属应用',
            dataIndex: 'appId',
            render: (appId: number) => appMap[appId] || `应用#${appId}`,
        },
        { title: '层级', dataIndex: 'level', width: 60 },
        {
            title: '关联状态',
            dataIndex: 'id',
            render: (id: number) => {
                const isAssociated = selectedRoleIds.includes(id);
                return <Tag color={isAssociated ? 'green' : 'default'}>{isAssociated ? '已关联' : '未关联'}</Tag>;
            },
        },
        {
            title: '操作',
            dataIndex: 'id',
            render: (id: number) => {
                const isAssociated = selectedRoleIds.includes(id);
                return (
                    <a onClick={() => toggleRole(id, isAssociated)} style={{ color: isAssociated ? '#ff4d4f' : '#1890ff' }}>
                        {isAssociated ? '解绑' : '关联'}
                    </a>
                );
            },
        },
    ];

    return (
        <PageContainer
            header={{
                title: `用户详情 - ${user?.username || '加载中...'}`,
                extra: <Button onClick={() => history.push('/system/user')}>返回列表</Button>,
            }}
        >
            <Spin spinning={loading}>
                <Tabs activeKey={tab} onChange={setTab}>
                    {/* 基本信息 Tab */}
                    <Tabs.TabPane tab="基本信息" key="basic">
                        <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
                            {user ? (
                                <Descriptions column={2} bordered>
                                    <Descriptions.Item label="ID">{user.id}</Descriptions.Item>
                                    <Descriptions.Item label="用户名">{user.username}</Descriptions.Item>
                                    <Descriptions.Item label="昵称">{user.nickname || '-'}</Descriptions.Item>
                                    <Descriptions.Item label="邮箱">{user.email || '-'}</Descriptions.Item>
                                    <Descriptions.Item label="手机">{user.phone || '-'}</Descriptions.Item>
                                    <Descriptions.Item label="员工号">{user.empNo || '-'}</Descriptions.Item>
                                    <Descriptions.Item label="状态">
                                        <Tag color={user.status === 1 ? 'green' : 'red'}>
                                            {user.status === 1 ? '正常' : '禁用'}
                                        </Tag>
                                    </Descriptions.Item>
                                    <Descriptions.Item label="最后登录">{user.lastLoginAt || '-'}</Descriptions.Item>
                                    <Descriptions.Item label="创建时间">{user.createdAt}</Descriptions.Item>
                                    <Descriptions.Item label="更新时间">{user.updatedAt}</Descriptions.Item>
                                </Descriptions>
                            ) : '暂无数据'}
                        </div>
                    </Tabs.TabPane>

                    {/* 关联应用 Tab */}
                    <Tabs.TabPane tab="关联应用" key="apps">
                        <ProTable
                            headerTitle="应用列表"
                            rowKey="id"
                            search={false}
                            pagination={{ pageSize: 10 }}
                            request={async () => {
                                const data = allApps.map(app => ({
                                    ...app,
                                    associated: selectedAppIds.includes(app.id),
                                }));
                                return { data, success: true, total: data.length };
                            }}
                            columns={appColumns}
                        />
                    </Tabs.TabPane>

                    {/* 关联角色 Tab - 只显示用户已关联应用下的角色 */}
                    <Tabs.TabPane tab="关联角色" key="roles">
                        <ProTable
                            headerTitle="角色列表（仅显示已关联应用下的角色）"
                            rowKey="id"
                            search={false}
                            pagination={{ pageSize: 10 }}
                            request={async () => {
                                // 关键过滤：只保留 appId 在 selectedAppIds 中的角色
                                const filteredRoles = allRoles.filter(role => selectedAppIds.includes(role.appId));
                                console.log('当前已关联应用ID:', selectedAppIds);
                                console.log('所有角色:', allRoles);
                                console.log('过滤后角色:', filteredRoles);
                                const data = filteredRoles.map(role => ({
                                    ...role,
                                    associated: selectedRoleIds.includes(role.id),
                                }));
                                return { data, success: true, total: data.length };
                            }}
                            columns={roleColumns}
                            locale={{ emptyText: '请先关联应用后再分配角色' }}
                        />
                    </Tabs.TabPane>
                </Tabs>
            </Spin>
        </PageContainer>
    );
};

export default UserDetail;