///
/// Copyright (C) 2015 Stratio (http://stratio.com)
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///         http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import {
    AfterViewInit,
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    forwardRef,
    Input,
    OnChanges,
    OnDestroy,
    OnInit,
    SimpleChange,
    SimpleChanges,
    ViewChildren,
    ViewChild,
    ElementRef,
    HostListener
} from '@angular/core';
import {
    ControlValueAccessor, FormControl, NG_VALIDATORS, NG_VALUE_ACCESSOR, FormBuilder, FormArray, FormGroup, NgForm
} from '@angular/forms';
import { Subscription } from 'rxjs/Subscription';

@Component({
    selector: 'form-string-list',
    templateUrl: './form-string-list.template.html',
    styleUrls: ['./form-string-list.styles.scss'],
    providers: [
        { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => FormListStringComponent), multi: true }
    ],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class FormListStringComponent implements ControlValueAccessor, OnInit, OnDestroy {

    @Input() public formListData: any;
    @Input() public label: string = '';
    @Input() public qaTag = '';

    @ViewChild('inputForm') public inputForm: NgForm;
    @ViewChild('newElement') newElementInput: ElementRef;


    public item: any = {};
    public form: FormGroup;
    public items: FormArray;
    public innerInputContent = '';

    onChange = (_: any) => { };
    onTouched = () => { };

    addElementFocus() {
        this.newElementInput.nativeElement.focus();
    }

    onkeyPress($event: any) {
        if($event.keyCode === 13) {
            this.items.push(this.createItem(this.innerInputContent));
            this.innerInputContent = '';
        }
    }

    constructor(private formBuilder: FormBuilder, private _cd: ChangeDetectorRef) { };

    ngOnInit() {

        this.items = this.formBuilder.array([]);
        this.form = new FormGroup({
            items: this.items
        });
    }

    //create empty item
    createItem(value: any): FormControl {
        return this.formBuilder.control(value);
    }

    deleteItem(i: number) {
        this.items.removeAt(i);
    }
    
    addItem(): void {
       
    }

    getItemClass(): string {
        const length = this.formListData.fields.length;
        if(length === 1) {
            return 'col-xs-6';
        } else if(length<4) {
            return 'col-xs-' + 12/length;
        } else {
            return 'col-xs-4';
        }
    }
    writeValue(data: any): void {
        if (data && Array.isArray(data) && data.length) {
            this.items.controls = [];
            for (let value of data) {
                let item: any = {};
                for (let field of this.formListData.fields) {
                    item[field.propertyId] = [value[field.propertyId]];
                }
                let form: FormGroup = this.formBuilder.group(item);
                this.items.push(form);
            }
            this._cd.detectChanges();
        } else {
            this.addItem();
        }
    }


    // Registry the change function to propagate internal model changes
    registerOnChange(fn: (_: any) => void): void {
        this.onChange = fn;
    }

    registerOnTouched(fn: any): void {
        this.onTouched = fn;
    }

    setDisabledState(isDisabled: boolean): void {

    }

    changeValue(value: any): void {
        this.onChange(this.items.value);
    }

    ngOnDestroy(): void {

    }
}
