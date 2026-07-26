import React, { useState, useCallback, useEffect } from 'react';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Tabs, Descriptions, Tag, Button, Spin, message, Empty } from 'antd';
import { history, useParams, request } from '@umijs/max';

const PAGE_SIZE_LARGE = 9999;

const PERM_TYPE_MAP: Record<string, string> = {
    MENU: '菜单',
    API: 'API',
    BUTTON: '按钮',
    DATA: '数据',
};

const PermissionDetail: React.FC = () => {
    const params = useParams<{ id: string }>();
    const permId = params.id;

    const [tab, setTab] = useState<string>('basic');
    const [loading, setLoading] = useState<boolean>(true);
    const [permission, setPermission] = useState<any>(null);

    // 应用名称映射
    const [appMap, setAppMap] = useState<Record<number, string>>({});

    // 资源相关
    const [allResources, setAllResources] = useState<any[]>([]);
    const [selectedResourceIds, setSelectedResourceIds] = useState<number[]>([]);

    const fetchData = useCallback(async () => {
        if (!permId) return;
        setLoading(true);
        try {
            // 第一步：获取权限基本信息
            const [permRes, appsRes] = await Promise.all([
                request<{ data: any }>(`/api/system/permission/${permId}`),
                request<{ data: any[] }>('/api/system/app/list'),
            ]);

            setPermission(permRes.data);

            // 构建应用名称映射
            const map: Record<number, string> = {};
            (appsRes.data || []).forEach((app: any) => {
                map[app.id] = app.name;
            });
            setAppMap(map);

            // 第二步：获取关联数据（允许失败）
            try {
                const [resourcesRes, permResourcesRes] = await Promise.all([
                    request<{ data: any[] }>(`/api/system/resource?pageSize=${PAGE_SIZE_LARGE}`),
                    request<{ data: any[] }>(`/api/system/permission/${permId}/resources`),
                ]);

                setAllResources(resourcesRes.data || []);
                setSelectedResourceIds((permResourcesRes.data || []).map((item: any) => item.id));
            } catch (e) {
                message.warning('部分关联数据加载失败，请刷新页面重试');
            }
        } catch (error) {
            message.error('加载权限数据失败');
        } finally {
            setLoading(false);
        }
    }, [permId]);

    useEffect(() => {
        if (permId) {
            fetchData();
        }
    }, [permId, fetchData]);

    // 切换资源关联状态
    const toggleResource = async (resourceId: number, currentlyAssociated: boolean) => {
        let newIds: number[];
        if (currentlyAssociated) {
            newIds = selectedResourceIds.filter(id => id !== resourceId);
        } else {
            newIds = [...selectedResourceIds, resourceId];
        }
        try {
            await request(`/api/system/permission/${permId}/resources`, {
                method: 'PUT',
                data: newIds,
            });
            setSelectedResourceIds(newIds);
            message.success(currentlyAssociated ? '已解绑资源' : '已关联资源');
        } catch {
            message.error('操作失败');
        }
    };

    if (!permId) return <Empty description="权限ID不能为空" />;

    // 资源表格列
    const resourceColumns = [
        { title: 'ID', dataIndex: 'id', width: 70 },
        { title: '资源路径/名称', dataIndex: 'name', ellipsis: true },
        {
            title: '资源类型',
            dataIndex: 'resType',
            width: 80,
            valueEnum: {
                MENU: { text: '菜单' },
                API: { text: 'API' },
                BUTTON: { text: '按钮' },
                PAGE_ELEMENT: { text: '页面元素' },
            },
        },
        { title: 'HTTP方法', dataIndex: 'action', width: 80 },
        { title: '匹配方式', dataIndex: 'matchType', width: 80 },
        {
            title: '所属应用',
            dataIndex: 'appId',
            render: (appId: number) => appMap[appId] || `应用#${appId}`,
        },
        {
            title: '关联状态',
            dataIndex: 'id',
            width: 80,
            render: (id: number) => {
                const isAssociated = selectedResourceIds.includes(id);
                return <Tag color={isAssociated ? 'green' : 'default'}>{isAssociated ? '已关联' : '未关联'}</Tag>;
            },
        },
        {
            title: '操作',
            dataIndex: 'id',
            width: 80,
            render: (id: number) => {
                const isAssociated = selectedResourceIds.includes(id);
                return (
                    <a onClick={() => toggleResource(id, isAssociated)} style={{ color: isAssociated ? '#ff4d4f' : '#1890ff' }}>
                        {isAssociated ? '解绑' : '关联'}
                    </a>
                );
            },
        },
    ];

    return (
        <PageContainer
            header={{
                title: `权限详情 - ${permission?.name || '加载中...'}`,
                extra: <Button onClick={() => history.push('/system/permission')}>返回列表</Button>,
            }}
        >
            <Spin spinning={loading}>
                <Tabs activeKey={tab} onChange={setTab}>
                    {/* 基本信息 Tab */}
                    <Tabs.TabPane tab="基本信息" key="basic">
                        <div style={{ padding: 24, background: '#fff', borderRadius: 8 }}>
                            {permission ? (
                                <Descriptions column={2} bordered>
                                    <Descriptions.Item label="ID">{permission.id}</Descriptions.Item>
                                    <Descriptions.Item label="权限编码">{permission.code}</Descriptions.Item>
                                    <Descriptions.Item label="权限名称">{permission.name}</Descriptions.Item>
                                    <Descriptions.Item label="所属应用">
                                        {appMap[permission.appId] || `应用#${permission.appId}`}
                                    </Descriptions.Item>
                                    <Descriptions.Item label="权限类型">
                                        <Tag>{PERM_TYPE_MAP[permission.permType] || permission.permType}</Tag>
                                    </Descriptions.Item>
                                    <Descriptions.Item label="分类">{permission.category || '-'}</Descriptions.Item>
                                    <Descriptions.Item label="描述">{permission.description || '-'}</Descriptions.Item>
                                    <Descriptions.Item label="状态">
                                        <Tag color={permission.status === 1 ? 'green' : 'red'}>
                                            {permission.status === 1 ? '正常' : '禁用'}
                                        </Tag>
                                    </Descriptions.Item>
                                    <Descriptions.Item label="创建时间">{permission.createdAt}</Descriptions.Item>
                                    <Descriptions.Item label="更新时间">{permission.updatedAt}</Descriptions.Item>
                                </Descriptions>
                            ) : '暂无数据'}
                        </div>
                    </Tabs.TabPane>

                    {/* 关联资源 Tab */}
                    <Tabs.TabPane tab="关联资源" key="resources">
                        <ProTable
                            headerTitle="资源列表"
                            rowKey="id"
                            search={false}
                            pagination={{ pageSize: 10 }}
                            request={async () => {
                                const data = allResources.map(res => ({
                                    ...res,
                                    associated: selectedResourceIds.includes(res.id),
                                }));
                                return { data, success: true, total: data.length };
                            }}
                            columns={resourceColumns}
                        />
                    </Tabs.TabPane>
                </Tabs>
            </Spin>
        </PageContainer>
    );
};

export default PermissionDetail;
