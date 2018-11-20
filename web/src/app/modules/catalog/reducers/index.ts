/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
import { createSelector } from 'reselect';
import { createFeatureSelector } from '@ngrx/store';

import * as fromRoot from 'reducers';
import * as fromCrossdata from './crossdata';

export interface CrossdataState {
   crossdata: fromCrossdata.State;
}

export interface State extends fromRoot.State {
   crossdata: CrossdataState;
}

export const reducers = {
   crossdata: fromCrossdata.reducer
};

export const getCrossdataState = createFeatureSelector<CrossdataState>('crossdata');

export const getCrossdataEntityState = createSelector(
   getCrossdataState,
   state => state.crossdata
);


// crossdata
export const getTablesList = createSelector(getCrossdataEntityState, fromCrossdata.getTableList);
export const getDatabases = createSelector(getCrossdataEntityState, fromCrossdata.getDatabases);
export const getSelectedDatabase =  createSelector(getCrossdataEntityState, fromCrossdata.getSelectedDatabase);
export const getQueryResult = createSelector(getCrossdataEntityState, fromCrossdata.getQueryResult);
export const getQueryError = createSelector(getCrossdataEntityState, fromCrossdata.getQueryError);
export const getSelectedTables = createSelector(getCrossdataEntityState, fromCrossdata.getSelectedTables);
export const isLoadingDatabases =  createSelector(getCrossdataEntityState, fromCrossdata.isLoadingDatabases);
export const isLoadingTables: any = createSelector(getCrossdataEntityState, fromCrossdata.isLoadingTables);
export const isLoadingQuery: any = createSelector(getCrossdataEntityState, fromCrossdata.isLoadingQuery);
export const getOpenedTables = createSelector(getCrossdataEntityState, (state) => state.openedTables);