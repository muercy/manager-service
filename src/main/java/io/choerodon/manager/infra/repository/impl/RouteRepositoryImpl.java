package io.choerodon.manager.infra.repository.impl;

import io.choerodon.core.convertor.ConvertHelper;
import io.choerodon.core.convertor.ConvertPageHelper;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.manager.domain.manager.entity.RouteE;
import io.choerodon.manager.domain.repository.RouteRepository;
import io.choerodon.manager.infra.common.annotation.RouteNotifyRefresh;
import io.choerodon.manager.infra.dataobject.RouteDO;
import io.choerodon.manager.infra.mapper.RouteMapper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author wuguokai
 */
@Component
public class RouteRepositoryImpl implements RouteRepository {

    private RouteMapper routeMapper;

    @Value("${eureka.client.serviceUrl.defaultZone}")
    private String registerUrl;

    private RestTemplate restTemplate = new RestTemplate();

    private static final String ADD_ZUUL_ROOT_URL = "/zuul";

    public RouteRepositoryImpl(RouteMapper routeMapper) {
        this.routeMapper = routeMapper;
    }

    @Override
    public RouteE queryRoute(RouteE routeE) {
        RouteDO routeDO = ConvertHelper.convert(routeE, RouteDO.class);
        return ConvertHelper.convert(routeMapper.selectOne(routeDO), RouteE.class);
    }

    @Override
    @RouteNotifyRefresh
    @Transactional(rollbackFor = CommonException.class)
    public RouteE addRoute(RouteE routeE) {
        if (routeE.getBuiltIn() == null) {
            routeE.setBuiltIn(false);
        }
        RouteDO routeDO = ConvertHelper.convert(routeE, RouteDO.class);
        try {
            int isInsert = routeMapper.insert(routeDO);
            if (isInsert != 1) {
                throw new CommonException("error.insert.route");
            }
            addOrUpdateRouteToGoRegister(routeDO, "error to add route to register server");
        } catch (DuplicateKeyException e) {
            if (routeMapper.selectCount(new RouteDO(routeE.getName())) > 0) {
                throw new CommonException("error.route.insert.nameDuplicate");
            } else {
                throw new CommonException("error.route.insert.pathDuplicate");
            }
        }
        return ConvertHelper.convert(routeMapper.selectByPrimaryKey(routeDO.getId()), RouteE.class);
    }


    @Override
    @RouteNotifyRefresh
    @Transactional(rollbackFor = CommonException.class)
    public RouteE updateRoute(RouteE routeE) {
        RouteDO oldRouteD = routeMapper.selectByPrimaryKey(routeE.getId());
        if (oldRouteD == null) {
            throw new CommonException("error.route.not.exist");
        }
        if (oldRouteD.getBuiltIn()) {
            throw new CommonException("error.route.updateBuiltIn");
        }
        RouteDO routeDO = ConvertHelper.convert(routeE, RouteDO.class);
        if (routeDO.getObjectVersionNumber() == null) {
            throw new CommonException("error.objectVersionNumber.empty");
        }
        routeDO.setBuiltIn(null);
        try {
            int isUpdate = routeMapper.updateByPrimaryKeySelective(routeDO);
            if (isUpdate != 1) {
                throw new CommonException("error.update.route");
            }
            addOrUpdateRouteToGoRegister(routeDO, "error to update route to register server");
        } catch (DuplicateKeyException e) {
            if (routeE.getName() != null && routeMapper.selectCount(new RouteDO(routeE.getName())) > 0) {
                throw new CommonException("error.route.insert.nameDuplicate");
            } else {
                throw new CommonException("error.route.insert.pathDuplicate");
            }
        }
        return ConvertHelper.convert(routeMapper.selectByPrimaryKey(routeE.getId()), RouteE.class);
    }


    @Override
    @RouteNotifyRefresh
    public boolean deleteRoute(RouteE routeE) {
        RouteDO routeDO = ConvertHelper.convert(routeE, RouteDO.class);
        int isDelete = routeMapper.delete(routeDO);
        if (isDelete != 1) {
            throw new CommonException("error.delete.route");
        }
        return true;
    }

    @Override
    public List<RouteE> getAllRoute() {
        List<RouteDO> routeDOList = routeMapper.selectAll();
        return ConvertHelper.convertList(routeDOList, RouteE.class);
    }

    @Override
    public List<RouteE> addRoutesBatch(List<RouteE> routeEList) {
        return routeEList.stream().map(this::addRoute).collect(Collectors.toList());
    }

    @Override
    public Page<RouteE> pageAllRoutes(PageRequest pageRequest, RouteDO routeDO, String params) {
        Page<RouteDO> routeDOPage = PageHelper.doPageAndSort(pageRequest, () -> routeMapper.selectRoutes(routeDO, params));
        return ConvertPageHelper.convertPage(routeDOPage, RouteE.class);
    }

    @Override
    public int countRoute(RouteDO routeDO) {
        return routeMapper.selectCount(routeDO);
    }

    private void addOrUpdateRouteToGoRegister(RouteDO routeDO, String message) {
        String zuulRootUrl = getZuulRootUrl();
        ResponseEntity<Void> response = restTemplate.postForEntity(zuulRootUrl, routeDO, Void.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new CommonException("{}, exception: {}", message, response.getStatusCodeValue());
        }
    }

    private String getZuulRootUrl() {
        if (!registerUrl.endsWith("/eureka") && !registerUrl.endsWith("/eureka/")) {
            throw new CommonException("error.illegal.register-service.url");
        }
        String[] array = registerUrl.split("/eureka");
        String registerHost = array[0];
        return registerHost + ADD_ZUUL_ROOT_URL;
    }
}
