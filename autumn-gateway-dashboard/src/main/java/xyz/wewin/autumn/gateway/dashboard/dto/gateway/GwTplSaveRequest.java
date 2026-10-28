package xyz.wewin.autumn.gateway.dashboard.dto.gateway;

import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwTplDefinition;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwTplField;

import java.util.List;

/**
 * 模板保存请求（v2）：模板定义 + 配置项列表。
 * 更新时按「整体替换」语义处理 fields（先删后插）。
 */
public class GwTplSaveRequest {

    private GwTplDefinition def;
    private List<GwTplField> fields;

    public GwTplDefinition getDef() { return def; }
    public void setDef(GwTplDefinition def) { this.def = def; }
    public List<GwTplField> getFields() { return fields; }
    public void setFields(List<GwTplField> fields) { this.fields = fields; }
}
