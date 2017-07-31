/*
 * Copyright © 2017 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
*/

import React, {Component} from 'react';
import RuleBook from 'components/RulesEngineHome/RuleBook';
import {Input} from 'reactstrap';
import RulesEngineStore, {RULESENGINEACTIONS} from 'components/RulesEngineHome/RulesEngineStore';
import Fuse from 'fuse.js';
import isEmpty from 'lodash/isEmpty';

require('./RulesBooksTab.scss');

export default class RuleBooksTab extends Component {
  state = {
    searchStr: '',
    rulebooks: RulesEngineStore.getState().rulebooks.list
  };

  componentDidMount() {
    RulesEngineStore.subscribe(() => {
      let {rulebooks} = RulesEngineStore.getState();
      this.setState({
        rulebooks: rulebooks.list
      });
    });
  }

  updateSearchStr = (e) => {
    this.setState({
      searchStr: e.target.value
    });
  }

  createNewRuleBook = () => {
    RulesEngineStore.dispatch({
      type: RULESENGINEACTIONS.SETCREATERULEBOOK,
      payload:{
        isCreate: true
      }
    });
  };

  renderRulebooks() {

    if (isEmpty(this.state.searchStr)) {
      return (
        this.state
          .rulebooks
          .map(rulebook => {
            return (<RuleBook bookDetails={rulebook}/>);
          })
      );
    }

    // TODO not sure about performance
    const fuseOptions = {
      caseSensitive: true,
      threshold: 0,
      location: 0,
      distance: 100,
      maxPatternLength: 32,
      keys: [
        "id",
        "user",
        "rules",
        "description",
        "source"
      ]
    };

    let fuse = new Fuse(this.state.rulebooks, fuseOptions);
    return (
      fuse.search(this.state.searchStr)
        .map(rulebook => {
          return (<RuleBook bookDetails={rulebook}/>);
        })
    );
  }

  render() {
    return (
      <div className="rule-books-tab">
        <Input
          placeholder="Search Rulebook by name, owner or description"
          value={this.state.searchStr}
          onChange={this.updateSearchStr}
        />
        <div className="rule-books-container">
          <div
            className="rule-book center"
            onClick={this.createNewRuleBook}
          >
            <strong> Create a new Rulebook </strong>
            <div>
              +
            </div>
          </div>
          {this.renderRulebooks()}
        </div>
      </div>
    );
  }
}