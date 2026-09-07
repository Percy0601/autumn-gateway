import React, { useState, useCallback, useEffect } from 'react';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Tabs, Descriptions, Tag, Button, Spin, message, Empty } from 'antd';
import { history, useParams, useSearchParams, request } from '@umijs/max';

const PAGE_SIZE_LARGE = 9999;

const RoleDetail: React.FC = () => {
    const params = useParams<{ id: string }>();
    const roleId = params.id;
    const [searchParams] = useSearchParams();

    const [tab, setTab] = useState<string>(() => searchParams.get('tab') || 'basic');
    const [loading, setLoading] = useState<boolean>(true);
    const [role, setRole] = useState<any>(null);

    // 用于强制刷新权限/用户表格
    const [permVersion, setPermVersion] = useState(0);
    const [userVersion, setUserVersion] = useState(0);

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
            const [roleRes, appsRes] = await Promise.all([
                request<{ data: any }>(`/api/system/role/${roleId}`),
                request<{ data: any[] }>('/api/system/app/list'),
            ]);

            setRole(roleRes.data);

            const map: Record<number, string> = {};
            (appsRes.data || []).forEach((app: any) => {
                map[app.id] = app.name;
            });
            setAppMap(map);

            // 加载关联数据
            try {
                const [permsRes, rolePermsRes, usersRes, roleUsersRes] = await Promise.all([
                    request<{ data: any[] }>(`/api/system/permission?pageSize=${PAGE_SIZE_LARGE}`),
                    request<{ data: any[] }>(`/api/system/role/${roleId}/permissions`),
                    request<{ data: any[] }>(`/api/system/user?pageSize=${PAGE_SIZE_LARGE}`),
                    request<{ data: any[] }>(`/api/system/role/${roleId}/users`),
                ]);

                setAllPermissions(permsRes.data || []);
                setSelectedPermissionIds((rolePermsRes.data || []).map((item: any) => item.id));
                setAllUsers(usersRes.data || []);
                setSelectedUserIds((roleUsersRes.data || []).map((item: any) => item.id));
            } catch (e) {
                message.warning('部分关联数据加载失败，请刷新页面重试');
            }
        } catch (error) {
            message.error('加载角色数据失败');
        } finally {
            setLoading(false);
        }
    }, [roleId]);

    useEffect(() => {
        if (roleId) fetchRoleData();
    }, [roleId, fetchRoleData]);

    // 切换权限关联状态
    const togglePermission = async (permId: number, currentlyAssociated: boolean) => {
        const newIds = currentlyAssociated
            ? selectedPermissionIds.filter(id => id !== permId)
            : [...selectedPermissionIds, permId];

        await request(`/api/system/role/${roleId}/permissions`, {
            method: 'PUT',
            data: newIds,
        });
        setSelectedPermissionIds(newIds);
        setPermVersion(v => v + 1); // 触发表格重渲染
        message.success(currentlyAssociated ? '已解绑权限' : '已关联权限');
    };

    // 切换用户关联状态
    const toggleUser = async (userId: number, currentlyAssociated: boolean) => {
        const newIds = currentlyAssociated
            ? selectedUserIds.filter(id => id !== userId)
            : [...selectedUserIds, userId];

        await request(`/api/system/role/${roleId}/users`, {
            method: 'PUT',
            data: newIds,
        });
        setSelectedUserIds(newIds);
        setUserVersion(v => v + 1); // 触发表格重渲染
        message.success(currentlyAssociated ? '已解绑用户' : '已关联用户');
    };

    if (!roleId) return <Empty description="角色ID不能为空" />;

    // 构建表格数据（带关联状态标记）
    const permDataSource = allPermissions.map(perm => ({
        ...perm,
        _associated: selectedPermissionIds.includes(perm.id),
    }));

    const userDataSource = allUsers.map(user => ({
        ...user,
        _associated: selectedUserIds.includes(user.id),
    }));

    // 权限表格列
    const permColumns = [
        { title: 'ID', dataIndex: 'id', width: 70 },
        { title: '权限编码', dataIndex: 'code', ellipsis: true, copyable: true },
        { title: '权限名称', dataIndex: 'name', ellipsis: true },
        {
            title: '所属应用',
            dataIndex: 'appId',
            width: 130,
            render: (_: any, r: any) => appMap[r.appId] || '-',
        },
        {
            title: '权限类型',
            dataIndex: 'permType',
            width: 70,
            valueEnum: { MENU: '菜单', API: 'API', BUTTON: '按钮', DATA: '数据' },
        },
        {
            title: '资源路径',
            dataIndex: 'resourcePath',
            ellipsis: true,
            width: 180,
            render: (_: any, r: any) => r.resourcePath ? <code>{r.resourcePath}</code> : '-',
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 60,
            render: (s: number) => <Tag color={s === 1 ? 'green' : 'red'}>{s === 1 ? '正常' : '禁用'}</Tag>,
        },
        {
            title: '关联',
            dataIndex: '_associated',
            width: 70,
            render: (_: any, r: any) => (
                <Tag color={r._associated ? 'green' : 'default'}>{r._associated ? '已关联' : '未关联'}</Tag>
            ),
        },
        {
            title: '操作',
            dataIndex: 'id',
            width: 70,
            render: (id: number, r: any) => (
                <a onClick={() => togglePermission(id, r._associated)}
                   style={{ color: r._associated ? '#ff4d4f' : '#1890ff' }}>
                    {r._associated ? '解绑' : '关联'}
                </a>
            ),
        },
    ];

    // 用户表格列
    const userColumns = [
        { title: 'ID', dataIndex: 'id', width: 70 },
        { title: '用户名', dataIndex: 'username', ellipsis: true, copyable: true },
        { title: '昵称', dataIndex: 'nickname', ellipsis: true },
        { title: '邮箱', dataIndex: 'email', ellipsis: true },
        {
            title: '状态',
            dataIndex: 'status',
            width: 60,
            render: (s: number) => <Tag color={s === 1 ? 'green' : 'red'}>{s === 1 ? '正常' : '禁用'}</Tag>,
        },
        {
            title: '关联',
            dataIndex: '_associated',
            width: 70,
            render: (_: any, r: any) => (
                <Tag color={r._associated ? 'green' : 'default'}>{r._associated ? '已关联' : '未关联'}</Tag>
            ),
        },
        {
            title: '操作',
            dataIndex: 'id',
            width: 70,
            render: (id: number, r: any) => (
                <a onClick={() => toggleUser(id, r._associated)}
                   style={{ color: r._associated ? '#ff4d4f' : '#1890ff' }}>
                    {r._associated ? '解绑' : '关联'}
                </a>
            ),
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

                    {/* 关联权限 Tab — 使用 dataSource+key 驱动，state 变化即刷新 */}
                    <Tabs.TabPane tab="关联权限" key="permissions">
                        <ProTable
                            key={`perm-${permVersion}`}
                            headerTitle={`权限列表（已关联 ${selectedPermissionIds.length} 项）`}
                            rowKey="id"
                            search={false}
                            pagination={{ pageSize: 10 }}
                            dataSource={permDataSource}
                            columns={permColumns}
                            options={false}
                        />
                    </Tabs.TabPane>

                    {/* 关联用户 Tab — 使用 dataSource+key 驱动，state 变化即刷新 */}
                    <Tabs.TabPane tab="关联用户" key="users">
                        <ProTable
                            key={`user-${userVersion}`}
                            headerTitle={`用户列表（已关联 ${selectedUserIds.length} 项）`}
                            rowKey="id"
                            search={false}
                            pagination={{ pageSize: 10 }}
                            dataSource={userDataSource}
                            columns={userColumns}
                            options={false}
                        />
                    </Tabs.TabPane>
                </Tabs>
            </Spin>
        </PageContainer>
    );
};

export default RoleDetail;
