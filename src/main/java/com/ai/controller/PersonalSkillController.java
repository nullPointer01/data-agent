package com.ai.controller;

import com.ai.skill.PersonalSkillService;
import com.ai.skill.SkillLearningService;
import com.ai.service.SkillService;
import com.ai.skill.dto.*;
import com.ai.api.ListPageResponse;
import org.springframework.web.bind.annotation.*;

/** 登录用户的程序性记忆入口，身份与归属由服务端确定。 */
@RestController
@RequestMapping("/api/v1/my/skills")
public class PersonalSkillController {
    private final PersonalSkillService personal;
    private final SkillLearningService learning;
    private final SkillService management;

    public PersonalSkillController(PersonalSkillService personal, SkillLearningService learning, SkillService management) {
        this.personal = personal; this.learning = learning; this.management = management;
    }

    /** 分页查看本人技能候选和已发布程序性记忆。 */
    @GetMapping public ListPageResponse<SkillResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return personal.list(page, size); }
    /** 从本人完成的持久任务提炼候选，来源问答由服务端读取。 */
    @PostMapping("/from-run/{runId}") public SkillResponse fromRun(@PathVariable String runId) { return learning.fromRun(runId); }
    /** 编辑检查后确认发布，尚未完成的来源任务不能发布。 */
    @PostMapping("/{id}/confirm") public SkillResponse confirm(@PathVariable String id, @RequestBody SkillRequest request) {
        return personal.confirm(id, request);
    }
    /** 拒绝候选；同一任务不会自动覆盖这个决定。 */
    @PostMapping("/{id}/reject") public SkillResponse reject(@PathVariable String id) { return personal.reject(id); }
    /** 修改本人技能并保存指令与依赖历史，不开放共享技能写入。 */
    @PutMapping("/{id}") public SkillMutationResponse update(@PathVariable String id, @RequestBody SkillRequest request) {
        personal.requireOwned(id); return management.updateSkill(id, request);
    }
    /** 删除本人技能，后续运行重新鉴权时不再能够激活。 */
    @DeleteMapping("/{id}") public SkillMutationResponse delete(@PathVariable String id) {
        personal.requireOwned(id); return management.deleteSkill(id);
    }
    /** 按页读取本人技能的历史版本。 */
    @GetMapping("/{id}/history") public ListPageResponse<SkillHistoryResponse> history(@PathVariable String id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        personal.requireOwned(id); return management.listHistory(id, page, size);
    }
    /** 回滚指令、资料与依赖，执行权限仍以当前 Agent 绑定为准。 */
    @PostMapping("/{id}/rollback/{version}") public SkillMutationResponse rollback(@PathVariable String id, @PathVariable int version) {
        personal.requireOwned(id); return management.rollbackSkill(id, version);
    }
}
