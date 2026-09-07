import React, { useState, useCallback, useEffect } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Descriptions, Tag, Button, Spin, message, Empty } from 'antd';
import { history, useParams, request } from '@umijs/max';

const PERM_TYPE_MAP: Record<string, string> = {
    MENU: '菜单', API: 'API', BUTTON: '按钮', DATA: '数据',
};

const PermissionDetail: React.FC = () => {
    const params = useParams<{ id: string }>();
    const permId = params.id;

    const [loading, setLoading] = useState<boolean>(true);
    const [permission, setPermission] = useState<any>(null);
    const [appMap, setAppMap] = useState<Record<number, string>>({});

    const fetchData = useCallback(async () => {
        if (!permId) return;
        setLoading(true);
        try {
            const [permRes, appsRes] = await Promise.all([
                request<{ data: any }>(`/api/system/permission/${permId}`),
                request<{ data: any[] }>('/api/system/app/list'),
            ]);

            setPermission(permRes.data);

            const map: Record<number, string> = {};
            (appsRes.data || []).forEach((app: any) => {
                map[app.id] = app.name;
            });
            setAppMap(map);
        } catch (error) {
            message.error('加载权限数据失败');
        } finally {
            setLoading(false);
        }
    }, [permId]);

    useEffect(() => {
        if (permId) fetchData();
    }, [permId, fetchData]);

    if (!permId) return <Empty description="权限ID不能为空" />;

    return (
        <PageContainer
            header={{
                title: `权限详情 - ${permission?.name || '加载中...'}`,
                extra: <Button onClick={() => history.push('/system/permission')}>返回列表</Button>,
            }}
        >
            <Spin spinning={loading}>
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
                                <Tag color="blue">{PERM_TYPE_MAP[permission.permType] || permission.permType}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="分类">{permission.category || '-'}</Descriptions.Item>
                            {/* --- 资源 URL 信息 --- */}
                            <Descriptions.Item label="资源路径 (URL)">
                                <code>{permission.resourcePath || '-'}</code>
                            </Descriptions.Item>
                            <Descriptions.Item label="HTTP方法">
                                <Tag color="green">{permission.httpMethod || 'ALL'}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="匹配方式">{permission.matchType || 'exact'}</Descriptions.Item>
                            <Descriptions.Item label="父级ID (菜单树)">{permission.parentId || 0}</Descriptions.Item>
                            <Descriptions.Item label="图标">{permission.icon || '-'}</Descriptions.Item>
                            <Descriptions.Item label="排序">{permission.sort || 0}</Descriptions.Item>
                            <Descriptions.Item label="菜单隐藏">{permission.hidden ? '是' : '否'}</Descriptions.Item>
                            {/* --- 通用 --- */}
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
            </Spin>
        </PageContainer>
    );
};

export default PermissionDetail;
