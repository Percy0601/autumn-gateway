import React, { useState, useCallback, useEffect } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Tabs, Descriptions, Tag, Button, Space, Spin, Transfer, message, Empty } from 'antd';
import { history, useParams, request } from '@umijs/max';

const UserDetail: React.FC = () => {
    const params = useParams<{ id: string }>();
    const userId = params.id;

    const [tab, setTab] = useState<string>('basic');
    const [loading, setLoading] = useState<boolean>(true);
    const [user, setUser] = useState<any>(null);

    // 应用相关
    const [allApps, setAllApps] = useState<any[]>([]);
    const [selectedApps, setSelectedApps] = useState<number[]>([]);

    // 角色相关
    const [allRoles, setAllRoles] = useState<any[]>([]);
    const [selectedRoles, setSelectedRoles] = useState<number[]>([]);

    const fetchUserData = useCallback(async () => {
        if (!userId) return;
        setLoading(true);
        try {
            // 并行请求四个接口
            const [userRes, appsRes, userAppsRes, rolesRes, userRolesRes] = await Promise.all([
                request<{ data: any }>(`/api/system/user/${userId}`),        // 用户基本信息
                request<{ data: any[] }>('/api/system/app/list'),           // 所有应用
                request<{ data: any[] }>(`/api/system/user/${userId}/apps`), // 用户已关联的应用
                request<{ data: any[] }>('/api/system/role/list'),          // 所有角色
                request<{ data: any[] }>(`/api/system/user/${userId}/roles`), // 用户已关联的角色
            ]);

            setUser(userRes.data);
            setAllApps(appsRes.data || []);

            // 从用户已关联的应用列表中提取 appId 数组
            const userAppIds = (userAppsRes.data || []).map((item: any) => item.appId);
            setSelectedApps(userAppIds);

            setAllRoles(rolesRes.data || []);

            // 从用户已关联的角色列表中提取 roleId 数组
            const userRoleIds = (userRolesRes.data || []).map((item: any) => item.roleId);
            setSelectedRoles(userRoleIds);
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

    const saveApps = async () => {
        await request(`/api/system/user/${userId}/apps`, {
            method: 'PUT',
            data: { appIds: selectedApps },
        });
        message.success('应用关联已更新');
    };

    const saveRoles = async () => {
        await request(`/api/system/user/${userId}/roles`, {
            method: 'PUT',
            data: { roleIds: selectedRoles },
        });
        message.success('角色关联已更新');
    };

    if (!userId) return <Empty description="用户ID不能为空" />;

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
                            ) : (
                                '暂无数据'
                            )}
                        </div>
                    </Tabs.TabPane>

                    {/* 所属应用 Tab */}
                    <Tabs.TabPane tab="所属应用" key="apps">
                        <Transfer
                            dataSource={allApps.map(app => ({
                                key: app.id,
                                title: `${app.name} (${app.appid})`,
                            }))}
                            targetKeys={selectedApps}
                            onChange={setSelectedApps}
                            render={item => item.title}
                            titles={['可选应用', '已选应用']}
                            showSearch
                            filterOption={(inputValue, item) =>
                                item.title.toLowerCase().includes(inputValue.toLowerCase())
                            }
                        />
                        <Button type="primary" onClick={saveApps} style={{ marginTop: 16 }}>
                            保存应用关联
                        </Button>
                    </Tabs.TabPane>

                    {/* 角色分配 Tab */}
                    <Tabs.TabPane tab="角色分配" key="roles">
                        <Transfer
                            dataSource={allRoles.map(role => ({
                                key: role.id,
                                title: `${role.name} (${role.code})`,
                            }))}
                            targetKeys={selectedRoles}
                            onChange={setSelectedRoles}
                            render={item => item.title}
                            titles={['可选角色', '已选角色']}
                            showSearch
                            filterOption={(inputValue, item) =>
                                item.title.toLowerCase().includes(inputValue.toLowerCase())
                            }
                        />
                        <Button type="primary" onClick={saveRoles} style={{ marginTop: 16 }}>
                            保存角色关联
                        </Button>
                    </Tabs.TabPane>
                </Tabs>
            </Spin>
        </PageContainer>
    );
};

export default UserDetail;