/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  forwardRef,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  ViewChildren,
  AfterViewInit,
  ViewChild,
  ViewContainerRef,
  AfterContentInit
} from '@angular/core';
import { ControlValueAccessor, FormControl, NG_VALIDATORS, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Subscription } from 'rxjs';

import { SpInputError } from './sp-input.models';
import { StModalService } from '@stratio/egeo';
import { VariableSelectorComponent } from '@app/shared/components/sp-input/variable-selector/variable-selector.component';


@Component({
  selector: 'sp-input',
  templateUrl: './sp-input.component.html',
  styleUrls: ['./sp-input.component.scss'],
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => SpInputComponent), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => SpInputComponent), multi: true }
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})

export class SpInputComponent implements ControlValueAccessor, OnChanges, OnInit, OnDestroy, AfterViewInit {

  @Input() placeholder = '';
  @Input() name = '';
  @Input() label = '';
  @Input() fieldType: 'text' | 'number' | 'password' = 'text';
  @Input() errors: SpInputError;
  @Input() qaTag: string;
  @Input() forceValidations = false;
  @Input() contextualHelp: string;
  @Input() maxLength: number;
  @Input() min: number;
  @Input() max: number;
  @Input() isFocused = false;
  @Input() readonly = false;
  @Input() step: number;
  @Input() parameters: any;
  @Input() showVars = false;
  @Input() customValidator: (_: string) => string;

  @Input()
  get value(): any {
    return this._value;
  }

  set value(value: any) {
    this._value = value;
  }

  @Output() change: EventEmitter<any> = new EventEmitter<any>();

  @ViewChildren('input') vc: any;
  @ViewChild('variableSelectorModal', { read: ViewContainerRef}) target: any;

  public disabled = false; // To check disable
  public focus = false;
  public internalControl: FormControl;
  public errorMessage: string = undefined;
  public selectVarMode = false;
  public filteredVariableList: Array<any>;
  public pristine = true;
  public selectedIndex = 0;
  public defaultList = true;
  public filteredVariableGroupList: Array<any>;

  public isParameterValue = false;
  public paramType = '';
  public paramValue = '';
  public parameterValue = '';
  public options = [];

  private _sub: Subscription;
  private _value: any;
  private _valueChangeSub: Subscription;
  private _internalInputModel: any = '';

  constructor(private _cd: ChangeDetectorRef,
    private _stModalService: StModalService) { }

  public onChange = (_: any) => { };
  public onTouched = () => { };

  public validate(control: FormControl): any {
    const errors = this.checkErrors(this.internalControl);
    if (this._sub) {
      this._sub.unsubscribe();
    }
    this._sub = control.statusChanges.subscribe(() => this.checkErrors(control));
    return errors && errors.length ? {
      input: errors
    } : null;
  }

  public ngOnChanges(change: any): void {
    if (this.forceValidations && this.internalControl) {
      this.writeValue(this.internalControl.value);
    }
    this._cd.markForCheck();
  }

  public ngOnInit(): void {
    this.internalControl = new FormControl(this._internalInputModel);
    this._valueChangeSub = this.internalControl.valueChanges.subscribe((value) => {
      this.valuechange();
    });
  }

  public ngAfterViewInit(): void {
    if (this.isFocused) {
      setTimeout(() => this.vc.first.nativeElement.focus());
    }
    if (this.forceValidations) {
      this.onChange(this.isParameterValue ? `{{{${this.paramValue}}}}` : this.internalControl.value);
    }
  }

  public ngOnDestroy(): void {
    if (this._valueChangeSub) {
      this._valueChangeSub.unsubscribe();
    }
    if (this._sub) {
      this._sub.unsubscribe();
    }
  }


  public valuechange() {
    this.pristine = false;
    this.onChange(this.isParameterValue ? `{{{${this.paramValue}}}}` : this.internalControl.value);
  }

  // When value is received from outside
  public writeValue(value: any): void {
    if (typeof value === 'object') {
      value = JSON.stringify(value);
    }
    this.isParameterValue = false;
    const reg = /^{{.*.}}$/;
    const reg2 = /^{{{.*.}}}$/;
    if (this.parameters && value) {
      if (value.length > 6 && reg2.test(value)) {
        value = value.replace('{{{', '').replace('}}}', '');
        this.isParameterValue = true;
        this.paramValue = value;
        value = this.getParamLabel(value);
      } else if (value.length > 4 && reg.test(value)) {
        value = value.replace('{{', '').replace('}}', '');
        this.isParameterValue = true;
        this.paramValue = value;
        value = this.getParamLabel(value);
      }
    }
    if (this.forceValidations) {
      this.onChange(this.isParameterValue ? `{{{${this.paramValue}}}}` : value);
    }
    this._internalInputModel = value;
    this._value = value;

    if (this._internalInputModel !== value && this.value !== value) {
      setTimeout(() => this.internalControl.setValue(value));
    }
  }

  // Registry the change function to propagate internal model changes
  public registerOnChange(fn: (_: any) => void): void {
    this.onChange = fn;
  }

  // Registry the touch function to propagate internal touch events TODO: make this function.
  public registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  public setDisabledState(disable: boolean): void {
    this.disabled = disable;
    if (this.disabled && this.internalControl && this.internalControl.enabled) {
      this.internalControl.disable();
    } else if (!this.disabled && this.internalControl && this.internalControl.disabled) {
      this.internalControl.enable();
      setTimeout(() => this.internalControl.setValue(this.value));
    }
    this._cd.markForCheck();
  }

  public showError(): boolean {
    return this.errorMessage !== undefined &&
      (!this.pristine || this.forceValidations) && !this.focus && !this.disabled;
  }

  get labelQaTag(): string {
    return (this.qaTag || this.name) + '-label';
  }

  /** Style functions */
  public onFocus(event: Event): void {
    this.focus = true;
  }

  public onFocusOut(event: Event): void {
    this.focus = false;
  }

  public showParameterSelector() {
    this._stModalService.container = this.target;
    this._stModalService.show({
      modalTitle: 'Parameter selection',
      maxWidth: 770,
      inputs: {
        parameters: this.parameters,
        currentParameter: this.isParameterValue ? {
          paramType: this.paramType,
          value: this.paramValue
        } : null
      },
      outputs: {
        onSelectValue: (value) => {
          if (value && value.length) {
            this.isParameterValue = true;
            this.paramValue = value;
            this.internalControl.setValue(this.getParamLabel(value));
          }
          this._stModalService.close();
        }
      },
    }, VariableSelectorComponent);
  }

  public getParamLabel(value) {
    this.getParamValue(value);
    if (value.indexOf('Global.') === 0) {
      this.paramType = 'Global';
      return value.replace('Global.', '');
    } else if (value.indexOf('Environment.') === 0) {
      this.paramType = 'Environment';
      return value.replace('Environment.', '');
    } else if (value.indexOf('.') === -1) {
      this.paramType = 'Undefined';
      return value;
    } else {
      this.paramType = 'Custom';
      return value;
    }
  }

  public getParamValue(value) {
    if (this.parameters) {
      this.parameterValue = '';
      const params = value.split('.');
      if (params[0] === 'Global') {
        const param = this.parameters['globalVariables'].find(p => p.name === params[1]);
        if (param) {
          this.parameterValue = param.value;
        }
      } else if (params[0] === 'Environment') {
        const param = this.parameters['environmentVariables'].find(p => p.name === params[1]);
        if (param) {
          this.parameterValue = param.value;
        }
      }
    }
  }

  public onKeydown(event) {
    if (this.isParameterValue) {
      if (event.key === 'Backspace') {
        this.isParameterValue = false;
        this.internalControl.setValue('');
      } else if (![37, 39, 17, 67].includes(event.keyCode)) {
        event.preventDefault();
      }
    }

  }

  // When status change call this function to check if have errors
  private checkErrors(control: FormControl): string {
    const errors: { [key: string]: any } = control.errors;
    this.errorMessage = this.getErrorMessage(errors);
    this._cd.markForCheck();
    return this.errorMessage;
  }

  // Get error message in function of error list.
  private getErrorMessage(errors: { [key: string]: any }): string {
    if (this.customValidator) {
      const error = this.customValidator(this.internalControl.value);
      if (error && error.length) {
        return error;
      }
    }
    if (!errors) {
      return undefined;
    }
    if (!this.errors) {
      return '';
    }
    if (errors.hasOwnProperty('required')) {
      return this.errors.required || this.errors.generic || '';
    }
    if (errors.hasOwnProperty('fieldType')) {
      return this.errors.type || this.errors.generic || '';
    }
    if (errors.hasOwnProperty('minlength')) {
      return this.errors.minLength || this.errors.generic || '';
    }
    if (errors.hasOwnProperty('maxlength')) {
      return this.errors.maxLength || this.errors.generic || '';
    }
    if (errors.hasOwnProperty('pattern')) {
      return this.errors.pattern || this.errors.generic || '';
    }
    if (errors.hasOwnProperty('min')) {
      return this.errors.min || this.errors.generic || '';
    }
    if (errors.hasOwnProperty('max')) {
      return this.errors.max || this.errors.generic || '';
    }

    return '';
  }
}
